package nexa.compiler.ir;

import java.util.*;

import nexa.compiler.lang.NexaType;

/** Backend-independent verifier for Nexa IR. */
public final class NexaIrVerifier {
    public record Diagnostic(String message) {}

    public List<Diagnostic> verify(NexaIr.Program program) {
        List<Diagnostic> errors = new ArrayList<>();
        if (program == null) return List.of(new Diagnostic("IR program is null"));
        if (program.irVersion() != 1) errors.add(new Diagnostic("Unsupported IR version: " + program.irVersion()));
        if (program.functions().isEmpty()) errors.add(new Diagnostic("IR program has no functions"));
        for (NexaIr.Function function : program.functions()) verifyFunction(function, errors);
        return List.copyOf(errors);
    }

    private void verifyFunction(NexaIr.Function function, List<Diagnostic> errors) {
        if (function.name().isBlank()) errors.add(new Diagnostic("Function name is blank"));
        if (function.blocks().isEmpty()) {
            errors.add(new Diagnostic("Function has no blocks: " + function.name()));
            return;
        }

        Map<Integer, NexaIr.Block> blocks = new LinkedHashMap<>();
        Map<Integer, NexaIr.Value> values = new HashMap<>();
        Map<String, NexaIr.Local> locals = new LinkedHashMap<>();
        Map<String, Integer> stores = new HashMap<>();

        for (NexaIr.Local local : function.locals()) {
            if (locals.put(local.name(), local) != null) errors.add(new Diagnostic("Duplicate local: " + local.name()));
        }
        if (function.blocks().get(0).id() != 0) errors.add(new Diagnostic("Function entry block must be block 0"));

        for (NexaIr.Block block : function.blocks()) {
            if (blocks.put(block.id(), block) != null) errors.add(new Diagnostic("Duplicate block id: " + block.id()));
            if (block.id() < 0) errors.add(new Diagnostic("Negative block id: " + block.id()));
        }

        for (NexaIr.Block block : function.blocks()) {
            for (NexaIr.Instruction instruction : block.instructions()) {
                if (instruction.result() != null) {
                    NexaIr.Value previous = values.put(instruction.result().id(), instruction.result());
                    if (previous != null) errors.add(new Diagnostic("SSA value defined more than once: v" + instruction.result().id()));
                }
            }
        }

        for (NexaIr.Block block : function.blocks()) {
            for (NexaIr.Instruction instruction : block.instructions()) {
                verifyInstruction(instruction, values, locals, stores, errors);
            }
            verifyTerminator(block.terminator(), values, blocks.keySet(), errors);
        }

        for (Map.Entry<String, NexaIr.Local> entry : locals.entrySet()) {
            if (entry.getValue().constant() && stores.getOrDefault(entry.getKey(), 0) > 1) {
                errors.add(new Diagnostic("Const local assigned more than once: " + entry.getKey()));
            }
        }
    }

    private void verifyInstruction(
            NexaIr.Instruction instruction,
            Map<Integer, NexaIr.Value> values,
            Map<String, NexaIr.Local> locals,
            Map<String, Integer> stores,
            List<Diagnostic> errors) {

        if (instruction.result() != null && instruction.result().id() < 0) {
            errors.add(new Diagnostic("Negative SSA value id: v" + instruction.result().id()));
        }

        if (instruction instanceof NexaIr.Const constant) {
            if (constant.value() == null && !NexaType.same(constant.result().type(), NexaType.OBJECT)) {
                errors.add(new Diagnostic("Null constant requires OBJECT type"));
            }
            return;
        }

        if (instruction instanceof NexaIr.LoadLocal load) {
            if ("msg".equals(load.name()) || "message".equals(load.name())) {
                return;
            }
            NexaIr.Local local = locals.get(load.name());
            if (local == null) errors.add(new Diagnostic("Load of unknown local: " + load.name()));
            else if (!assignable(local.type(), load.result().type())) errors.add(new Diagnostic("Local load type mismatch for " + load.name()));
            return;
        }

        if (instruction instanceof NexaIr.StoreLocal store) {
            if ("msg".equals(store.name()) || "message".equals(store.name())) {
                return;
            }
            NexaIr.Local local = locals.get(store.name());
            if (local == null) {
                errors.add(new Diagnostic("Store to unknown local: " + store.name()));
            } else {
                stores.merge(store.name(), 1, Integer::sum);
                if (local.constant() && !store.constant()) errors.add(new Diagnostic("Cannot assign const local: " + store.name()));
                if (!assignable(local.type(), store.value().type())) {
                    errors.add(new Diagnostic("Local store type mismatch for " + store.name()
                            + ": expected " + local.type().displayName()
                            + ", got " + store.value().type().displayName()));
                }
            }
            return;
        }

        if (instruction instanceof NexaIr.LoadField load) {
            verifyFieldType(load.target().type(), load.field(), load.result().type(), errors);
            return;
        }

        if (instruction instanceof NexaIr.StoreField store) {
            NexaType fieldType = fieldType(store.target().type(), store.field());
            if (!assignable(fieldType, store.value().type())) errors.add(new Diagnostic("Field store type mismatch for " + store.field()));
            return;
        }

        if (instruction instanceof NexaIr.LoadIndex load) {
            verifyIndexType(load.target().type(), load.index().type(), load.result().type(), errors);
            return;
        }

        if (instruction instanceof NexaIr.StoreIndex store) {
            NexaType elementType = indexType(store.target().type());
            if (!assignable(elementType, store.value().type())) errors.add(new Diagnostic("Indexed store type mismatch"));
            return;
        }

        if (instruction instanceof NexaIr.ArrayCreate array) {
            if (!NexaType.same(array.result().type(), new NexaType.Array(array.elementType()))) {
                errors.add(new Diagnostic("ArrayCreate result type does not match element type"));
            }
            for (NexaIr.Value value : array.values()) {
                if (!assignable(array.elementType(), value.type())) {
                    errors.add(new Diagnostic("Array element type mismatch: expected "
                            + array.elementType().displayName() + ", got " + value.type().displayName()));
                }
            }
            return;
        }

        if (instruction instanceof NexaIr.ObjectCreate object) {
            if (!NexaType.same(object.result().type(), object.type())) {
                errors.add(new Diagnostic("ObjectCreate result type does not match object type"));
            }
            for (Map.Entry<String, NexaIr.Value> entry : object.fields().entrySet()) {
                NexaType expected = object.type().fields().get(entry.getKey());
                if (expected == null || !assignable(expected, entry.getValue().type())) {
                    errors.add(new Diagnostic("Object field type mismatch: " + entry.getKey()));
                }
            }
            return;
        }

        if (instruction instanceof NexaIr.Unary unary) {
            if (unary.op().equals("!")) {
                if (!NexaType.same(unary.operand().type(), NexaType.BOOLEAN)) errors.add(new Diagnostic("Logical negation requires BOOLEAN"));
                if (!NexaType.same(unary.result().type(), NexaType.BOOLEAN)) errors.add(new Diagnostic("Logical negation result must be BOOLEAN"));
            } else if (!NexaType.numeric(unary.operand().type())) {
                errors.add(new Diagnostic("Unary numeric operator requires numeric operand"));
            }
            return;
        }

        if (instruction instanceof NexaIr.Binary binary) {
            verifyBinary(binary, errors);
            return;
        }

        if (instruction instanceof NexaIr.HostCall host) {
            verifyHostCall(host, errors);
            return;
        }

        if (instruction instanceof NexaIr.Iterate iterate) {
            if (!isIterable(iterate.iterable().type())) errors.add(new Diagnostic("Iterate requires ARRAY or OBJECT"));
            if (!NexaType.same(iterate.result().type(), NexaType.OBJECT)) errors.add(new Diagnostic("Iterator handle must be OBJECT"));
            return;
        }

        if (instruction instanceof NexaIr.IterHasNext next) {
            if (!NexaType.same(next.iterator().type(), NexaType.OBJECT)) errors.add(new Diagnostic("Iterator handle must be OBJECT"));
            if (!NexaType.same(next.result().type(), NexaType.BOOLEAN)) errors.add(new Diagnostic("IterHasNext result must be BOOLEAN"));
            return;
        }

        if (instruction instanceof NexaIr.IterNext next) {
            if (!NexaType.same(next.iterator().type(), NexaType.OBJECT)) errors.add(new Diagnostic("Iterator handle must be OBJECT"));
            if (!NexaType.same(next.result().type(), next.elementType())) errors.add(new Diagnostic("IterNext result type mismatch"));
        }
    }

    private void verifyBinary(NexaIr.Binary binary, List<Diagnostic> errors) {
        NexaType left = binary.left().type();
        NexaType right = binary.right().type();
        String op = binary.op();

        if (op.equals("&&") || op.equals("||")) {
            if (!NexaType.same(left, NexaType.BOOLEAN) || !NexaType.same(right, NexaType.BOOLEAN)) errors.add(new Diagnostic("Logical operator requires BOOLEAN operands"));
            if (!NexaType.same(binary.result().type(), NexaType.BOOLEAN)) errors.add(new Diagnostic("Logical operator result must be BOOLEAN"));
            return;
        }

        if (Set.of("==", "!=", "<", "<=", ">", ">=").contains(op)) {
            if (!compatible(left, right)) errors.add(new Diagnostic("Incompatible comparison types"));
            if (!NexaType.same(binary.result().type(), NexaType.BOOLEAN)) errors.add(new Diagnostic("Comparison result must be BOOLEAN"));
            return;
        }

        if (!NexaType.numeric(left) || !NexaType.numeric(right)) {
            errors.add(new Diagnostic("Arithmetic requires numeric operands"));
            return;
        }

        NexaType expected = commonNumeric(left, right);
        if (!assignable(expected, binary.result().type())) errors.add(new Diagnostic("Arithmetic result type mismatch"));
    }

    private void verifyHostCall(NexaIr.HostCall host, List<Diagnostic> errors) {
        if (host.signature().parameters().size() != host.args().size()) {
            errors.add(new Diagnostic("Host signature arity mismatch for " + host.capability().qualifiedName()));
            return;
        }

        for (int i = 0; i < host.args().size(); i++) {
            NexaType expected = host.signature().parameters().get(i);
            NexaType actual = host.args().get(i).type();
            if (!hostCompatible(expected, actual)) {
                errors.add(new Diagnostic("Host argument type mismatch for "
                        + host.capability().qualifiedName() + " argument " + i
                        + ": expected " + expected.displayName() + ", got " + actual.displayName()));
            }
        }

        if (!NexaType.same(host.result().type(), host.signature().result())) {
            errors.add(new Diagnostic("Host result type does not match signature for "
                    + host.capability().qualifiedName()));
        }
    }

    private boolean hostCompatible(NexaType expected, NexaType actual) {
        return NexaType.same(expected, actual)
                || (NexaType.numeric(expected) && NexaType.numeric(actual) && rank(actual) <= rank(expected));
    }

    private void verifyTerminator(NexaIr.Terminator terminator, Map<Integer, NexaIr.Value> values, Set<Integer> blocks, List<Diagnostic> errors) {
        if (terminator == null) { errors.add(new Diagnostic("Block has no terminator")); return; }
        if (terminator instanceof NexaIr.Jump jump) {
            if (!blocks.contains(jump.targetBlock())) errors.add(new Diagnostic("Unknown control-flow target block: " + jump.targetBlock()));
        } else if (terminator instanceof NexaIr.Branch branch) {
            if (!values.containsKey(branch.condition().id())) errors.add(new Diagnostic("Branch uses unknown value: v" + branch.condition().id()));
            if (!NexaType.same(branch.condition().type(), NexaType.BOOLEAN)) errors.add(new Diagnostic("Branch condition must be BOOLEAN"));
            if (!blocks.contains(branch.trueBlock())) errors.add(new Diagnostic("Unknown true branch block: " + branch.trueBlock()));
            if (!blocks.contains(branch.falseBlock())) errors.add(new Diagnostic("Unknown false branch block: " + branch.falseBlock()));
        }
    }

    private boolean isIterable(NexaType type) { return type instanceof NexaType.Array || NexaType.same(type, NexaType.OBJECT); }

    private void verifyFieldType(NexaType target, String field, NexaType result, List<Diagnostic> errors) {
        NexaType expected = fieldType(target, field);
        if (!assignable(expected, result)) errors.add(new Diagnostic("Field load type mismatch for " + field));
    }

    private NexaType fieldType(NexaType target, String field) {
        if (target instanceof NexaType.ObjectType object) return object.fields().getOrDefault(field, NexaType.OBJECT);
        return NexaType.OBJECT;
    }

    private void verifyIndexType(NexaType target, NexaType index, NexaType result, List<Diagnostic> errors) {
        if (target instanceof NexaType.Array) {
            if (!NexaType.numeric(index)) errors.add(new Diagnostic("Array index must be numeric"));
            NexaType expected = indexType(target);
            if (!assignable(expected, result)) errors.add(new Diagnostic("Array load result type mismatch"));
        } else if (!NexaType.same(target, NexaType.OBJECT)) {
            errors.add(new Diagnostic("Indexing requires ARRAY or OBJECT"));
        }
    }

    private NexaType indexType(NexaType target) { return target instanceof NexaType.Array array ? array.element() : NexaType.OBJECT; }

    private boolean compatible(NexaType a, NexaType b) {
        return NexaType.same(a, b) || (NexaType.numeric(a) && NexaType.numeric(b)) || NexaType.same(a, NexaType.OBJECT) || NexaType.same(b, NexaType.OBJECT);
    }

    /** Recursive IR assignability. Arrays/objects are structural; OBJECT is dynamic. */
    private boolean assignable(NexaType expected, NexaType actual) {
        if (expected == null || actual == null) return false;
        if (NexaType.same(expected, actual)) return true;
        if (NexaType.same(expected, NexaType.OBJECT)) return true;
        if (NexaType.same(actual, NexaType.OBJECT)) return true;
        if (NexaType.numeric(expected) && NexaType.numeric(actual)) return rank(actual) <= rank(expected);

        if (expected instanceof NexaType.Array ea && actual instanceof NexaType.Array aa) {
            return assignable(ea.element(), aa.element());
        }

        if (expected instanceof NexaType.ObjectType eo && actual instanceof NexaType.ObjectType ao) {
            if (!eo.fields().keySet().equals(ao.fields().keySet())) return false;
            for (String name : eo.fields().keySet()) {
                if (!assignable(eo.fields().get(name), ao.fields().get(name))) return false;
            }
            return true;
        }

        return false;
    }

    private NexaType commonNumeric(NexaType a, NexaType b) {
        if (a.displayName().equals("FLOAT64") || b.displayName().equals("FLOAT64")) return NexaType.FLOAT64;
        if (a.displayName().equals("FLOAT32") || b.displayName().equals("FLOAT32")) return NexaType.FLOAT32;
        return rank(a) >= rank(b) ? a : b;
    }

    private int rank(NexaType type) {
        return switch (type.displayName()) {
            case "INT8", "UINT8" -> 1;
            case "INT16", "UINT16" -> 2;
            case "INT32", "UINT32" -> 3;
            case "INT64", "UINT64" -> 4;
            case "FLOAT32" -> 5;
            case "FLOAT64" -> 6;
            default -> 0;
        };
    }
}
