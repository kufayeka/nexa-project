package nexa.compiler.ir;

import java.util.*;

import nexa.compiler.lang.NexaType;
import nexa.compiler.lang.SourceSpan;

/**
 * Stable, typed intermediate representation between the Nexa frontend and
 * execution backends.  The IR deliberately knows nothing about concrete
 * plugins, assets, or JVM bytecode.
 */
public final class NexaIr {
    private NexaIr() {}

    public record Value(int id, NexaType type) {
        public Value {
            if (id < 0) throw new IllegalArgumentException("value id must be >= 0");
            Objects.requireNonNull(type, "type");
        }
    }

    public record Local(String name, NexaType type, boolean constant) {
        public Local {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
        }
    }

    /** Stable symbolic host capability. The implementation is resolved only by the runtime. */
    public record HostCapability(String namespace, String name, String version) {
        public HostCapability {
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(name, "name");
            version = version == null || version.isBlank() ? "1" : version;
            if (namespace.isBlank() || name.isBlank()) {
                throw new IllegalArgumentException("host capability namespace/name cannot be blank");
            }
        }

        public String qualifiedName() {
            return namespace + "." + name;
        }
    }

    public record HostSignature(List<NexaType> parameters, NexaType result) {
        public HostSignature {
            parameters = List.copyOf(parameters == null ? List.of() : parameters);
            Objects.requireNonNull(result, "result");
        }
    }

    public sealed interface Instruction permits
            Const, LoadLocal, StoreLocal,
            LoadTag, StoreTag,
            LoadField, StoreField, LoadIndex, StoreIndex,
            ArrayCreate, ObjectCreate,
            Unary, Binary,
            Call, HostCall,
            Iterate, IterHasNext, IterNext,
            Return {
        SourceSpan span();
        Value result();
    }

    public record Const(Value result, Object value, SourceSpan span) implements Instruction {}

    public record LoadLocal(Value result, String name, SourceSpan span) implements Instruction {}

    public record LoadTag(Value result, String name, SourceSpan span) implements Instruction {}

    public record StoreLocal(Value result, String name, Value value, boolean constant, SourceSpan span) implements Instruction {}

    public record StoreTag(Value result, String name, Value value, SourceSpan span) implements Instruction {}

    public record LoadField(Value result, Value target, String field, SourceSpan span) implements Instruction {}

    public record StoreField(Value result, Value target, String field, Value value, SourceSpan span) implements Instruction {}

    public record LoadIndex(Value result, Value target, Value index, SourceSpan span) implements Instruction {}

    public record StoreIndex(Value result, Value target, Value index, Value value, SourceSpan span) implements Instruction {}

    public record ArrayCreate(Value result, List<Value> values, NexaType elementType, SourceSpan span) implements Instruction {
        public ArrayCreate {
            values = List.copyOf(values);
            Objects.requireNonNull(elementType, "elementType");
        }
    }

    public record ObjectCreate(Value result, Map<String, Value> fields, NexaType.ObjectType type, SourceSpan span) implements Instruction {
        public ObjectCreate {
            fields = Map.copyOf(fields);
            Objects.requireNonNull(type, "type");
        }
    }

    public record Unary(Value result, String op, Value operand, SourceSpan span) implements Instruction {}

    public record Binary(Value result, String op, Value left, Value right, SourceSpan span) implements Instruction {}

    /** Direct/internal call. The target remains symbolic so later function lowering can resolve it. */
    public record Call(Value result, String target, List<Value> args, SourceSpan span) implements Instruction {
        public Call {
            args = List.copyOf(args);
        }
    }

    /** Dynamic host boundary. No plugin class name is embedded in the IR. */
    public record HostCall(
            Value result,
            HostCapability capability,
            HostSignature signature,
            List<Value> args,
            SourceSpan span) implements Instruction {
        public HostCall {
            Objects.requireNonNull(capability, "capability");
            Objects.requireNonNull(signature, "signature");
            args = List.copyOf(args);
        }
    }

    public record Iterate(Value result, Value iterable, SourceSpan span) implements Instruction {}
    public record IterHasNext(Value result, Value iterator, SourceSpan span) implements Instruction {}
    public record IterNext(Value result, Value iterator, NexaType elementType, SourceSpan span) implements Instruction {}

    public record Return(Value result, Value value, SourceSpan span) implements Instruction {}

    public sealed interface Terminator permits Jump, Branch, Stop {
        SourceSpan span();
    }

    public record Jump(int targetBlock, SourceSpan span) implements Terminator {}

    public record Branch(Value condition, int trueBlock, int falseBlock, SourceSpan span) implements Terminator {}

    public record Stop(SourceSpan span) implements Terminator {}

    public record Block(int id, List<Instruction> instructions, Terminator terminator) {
        public Block {
            instructions = List.copyOf(instructions);
            Objects.requireNonNull(terminator, "terminator");
        }
    }

    public record Function(
            String name,
            List<Local> locals,
            List<Block> blocks,
            NexaType returnType,
            List<String> parameters) {
        public Function {
            Objects.requireNonNull(name, "name");
            locals = List.copyOf(locals);
            blocks = List.copyOf(blocks);
            parameters = List.copyOf(parameters == null ? List.of() : parameters);
            Objects.requireNonNull(returnType, "returnType");
        }
    }

    public record Program(
            String name,
            List<Function> functions,
            Map<String, NexaType> types,
            int irVersion) {
        public Program {
            Objects.requireNonNull(name, "name");
            functions = List.copyOf(functions);
            types = Map.copyOf(types);
            if (irVersion <= 0) throw new IllegalArgumentException("invalid IR version");
        }
    }
}