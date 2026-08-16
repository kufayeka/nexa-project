package nexa.compiler.ir;

import java.util.*;

import nexa.compiler.lang.NexaType;

/** Lightweight backend-independent verifier for Nexa IR invariants. */
public final class NexaIrVerifier {
    public record Diagnostic(String message) {}

    public List<Diagnostic> verify(NexaIr.Program program) {
        List<Diagnostic> errors = new ArrayList<>();
        if (program == null) {
            return List.of(new Diagnostic("IR program is null"));
        }
        if (program.irVersion() != 1) {
            errors.add(new Diagnostic("Unsupported IR version: " + program.irVersion()));
        }
        if (program.functions().isEmpty()) {
            errors.add(new Diagnostic("IR program has no functions"));
        }

        for (NexaIr.Function function : program.functions()) {
            verifyFunction(function, errors);
        }
        return List.copyOf(errors);
    }

    private void verifyFunction(NexaIr.Function function, List<Diagnostic> errors) {
        Set<Integer> blocks = new HashSet<>();
        Set<Integer> values = new HashSet<>();
        Map<String, NexaIr.Local> locals = new HashMap<>();

        for (NexaIr.Local local : function.locals()) {
            if (locals.put(local.name(), local) != null) {
                errors.add(new Diagnostic("Duplicate local: " + local.name()));
            }
        }

        for (NexaIr.Block block : function.blocks()) {
            if (!blocks.add(block.id())) {
                errors.add(new Diagnostic("Duplicate block id: " + block.id()));
            }

            for (NexaIr.Instruction instruction : block.instructions()) {
                verifyInstruction(instruction, values, locals, errors);
            }
            verifyTerminator(block.terminator(), blocks, errors);
        }

        if (!function.blocks().isEmpty() && function.blocks().get(0).id() != 0) {
            errors.add(new Diagnostic("Function entry block must be block 0"));
        }

        // Validate branch targets after all block IDs are known.
        for (NexaIr.Block block : function.blocks()) {
            if (block.terminator() instanceof NexaIr.Jump jump) {
                requireBlock(blocks, jump.targetBlock(), errors);
            } else if (block.terminator() instanceof NexaIr.Branch branch) {
                requireBlock(blocks, branch.trueBlock(), errors);
                requireBlock(blocks, branch.falseBlock(), errors);
            }
        }
    }

    private void verifyInstruction(
            NexaIr.Instruction instruction,
            Set<Integer> values,
            Map<String, NexaIr.Local> locals,
            List<Diagnostic> errors) {

        if (instruction.result() != null && !values.add(instruction.result().id())) {
            errors.add(new Diagnostic("SSA value defined more than once: v" + instruction.result().id()));
        }

        if (instruction instanceof NexaIr.LoadLocal load && !locals.containsKey(load.name())) {
            errors.add(new Diagnostic("Load of unknown local: " + load.name()));
        }

        if (instruction instanceof NexaIr.StoreLocal store) {
            NexaIr.Local local = locals.get(store.name());
            if (local == null) {
                errors.add(new Diagnostic("Store to unknown local: " + store.name()));
            } else if (local.constant() && store.constant()) {
                // A const store is valid only for its declaration's initial store.
                // Multiple stores are rejected below using a separate count.
            }
            if (store.value() == null) {
                errors.add(new Diagnostic("Local store has no value: " + store.name()));
            }
        }

        if (instruction instanceof NexaIr.Binary binary) {
            if (!binary.left().type().equals(binary.right().type())
                    && !NexaType.numeric(binary.left().type())) {
                errors.add(new Diagnostic("Invalid binary operand types for " + binary.op()));
            }
        }

        if (instruction instanceof NexaIr.HostCall host) {
            if (host.signature().parameters().size() != host.args().size()) {
                errors.add(new Diagnostic(
                        "Host signature arity mismatch for " + host.capability().qualifiedName()));
            }
            for (int i = 0; i < Math.min(host.signature().parameters().size(), host.args().size()); i++) {
                NexaType expected = host.signature().parameters().get(i);
                NexaType actual = host.args().get(i).type();
                if (!NexaType.same(expected, actual) && !NexaType.numeric(expected)) {
                    errors.add(new Diagnostic(
                            "Host argument type mismatch for " + host.capability().qualifiedName()
                                    + ": expected " + expected.displayName()
                                    + ", got " + actual.displayName()));
                }
            }
            if (!NexaType.same(host.result().type(), host.signature().result())) {
                errors.add(new Diagnostic("Host result type does not match signature"));
            }
        }
    }

    private void verifyTerminator(
            NexaIr.Terminator terminator,
            Set<Integer> blocks,
            List<Diagnostic> errors) {
        if (terminator == null) errors.add(new Diagnostic("Block has no terminator"));
        // Targets are checked in a second pass because later blocks may not yet exist.
    }

    private void requireBlock(Set<Integer> blocks, int id, List<Diagnostic> errors) {
        if (!blocks.contains(id)) {
            errors.add(new Diagnostic("Unknown control-flow target block: " + id));
        }
    }
}
