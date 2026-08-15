package nexa.framework.runtime.domain.scripting.service;

import nexa.framework.runtime.domain.scripting.api.CompiledScript;
import nexa.framework.runtime.domain.scripting.api.ScriptExecutionResult;
import nexa.framework.runtime.domain.scripting.model.ScriptRuntimeContext;
import nexa.framework.runtime.api.model.RuntimeMessage;

import nexa.framework.runtime.domain.deployment.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NexaScriptCompilerTest {

    @Test
    void compileAndExecuteCoreLanguageFeatures() {
        NexaScriptCompiler compiler = new NexaScriptCompiler();
        CompiledScript script = compiler.compile("""
                val source = "{\\"items\\":[1,2,3],\\"name\\":\\"taiyo\\"}"
                val parsed = Json.parse(source)
                var total = 0
                for (var index = 0; index < parsed.items.length; index += 1) {
                    total += parsed.items[index]
                }

                msg.payload = {
                    total: total,
                    upper: parsed.name.toUpperCase(),
                    iso: DateTime.now().toISOString(),
                    replaced: Regex.replace("A-100", "-", "_"),
                    rounded: Math.round(10.6)
                }

                send(msg)
                """, "ws-a:flow-a:node-a");

        ScriptExecutionResult result = script.execute(new RuntimeMessage(), runtimeContext());

        assertFalse(result.stopped());
        RuntimeMessage emitted = result.emittedByPort().get("default").getFirst();
        assertEquals(6D, emitted.readRawValue("payload.total"));
        assertEquals("TAIYO", emitted.readRawValue("payload.upper"));
        assertEquals("A_100", emitted.readRawValue("payload.replaced"));
        assertEquals(11L, emitted.readRawValue("payload.rounded"));
        assertNotNull(emitted.readRawValue("payload.iso"));
    }

    @Test
    void nullSafetyAndCollectionsMustWork() {
        NexaScriptCompiler compiler = new NexaScriptCompiler();
        CompiledScript script = compiler.compile("""
                var values = [1, 2]
                values.push(3)
                val removed = values.splice(1, 1, 9, 10)
                val current = msg.payload?.speed ?? 0
                msg.payload = {
                    speed: current,
                    hasThree: values.includes(3),
                    joined: values.join("-"),
                    removed: removed.join("-"),
                    text: "  abc  ".trim().toUpperCase(),
                    date: "2026-07-12T10:15:30Z".toDate().toISOString()
                }
                send(["ok", "audit"], msg)
                return
                """, "ws-b:flow-b:node-b");

        RuntimeMessage message = new RuntimeMessage();
        ScriptExecutionResult result = script.execute(message, runtimeContext());

        assertEquals(2, result.emittedByPort().size());
        RuntimeMessage ok = result.emittedByPort().get("ok").getFirst();
        assertEquals(0D, ok.readRawValue("payload.speed"));
        assertEquals(true, ok.readRawValue("payload.hasThree"));
        assertEquals("1-9-10-3", ok.readRawValue("payload.joined"));
        assertEquals("2", ok.readRawValue("payload.removed"));
        assertEquals("ABC", ok.readRawValue("payload.text"));
        assertEquals("2026-07-12", ok.readRawValue("payload.date"));
    }

    @Test
    void switchMustExecuteMatchingCase() {
        NexaScriptCompiler compiler = new NexaScriptCompiler();
        CompiledScript script = compiler.compile("""
                val category = "production"
                var code = "unknown"

                switch (category) {
                    case "setup":
                        code = "S"
                    case "production":
                        code = "P"
                    default:
                        code = "D"
                }

                msg.payload = { code: code }
                send(msg)
                """, "ws-switch:flow-switch:node-switch");

        ScriptExecutionResult result = script.execute(new RuntimeMessage(), runtimeContext());
        RuntimeMessage emitted = result.emittedByPort().get("default").getFirst();
        assertEquals("P", emitted.readRawValue("payload.code"));
    }

    @Test
    void javaExtensionMustBeCallableFromScript() {
        NexaScriptCompiler compiler = new NexaScriptCompiler();
        CompiledScript script = compiler.compile("""
                msg.payload = {
                    upper: TestPlugin.upper("taiyo"),
                    sum: TestPlugin.sum(10, 5)
                }
                send(msg)
                """, "ws-ext:flow-ext:node-ext");

        ScriptExecutionResult result = script.execute(new RuntimeMessage(), runtimeContext());
        RuntimeMessage emitted = result.emittedByPort().get("default").getFirst();
        assertEquals("TAIYO", emitted.readRawValue("payload.upper"));
        assertEquals(15D, emitted.readRawValue("payload.sum"));
    }

    @Test
    void lambdaAndFunctionDeclarationMustWorkWithCollections() {
        NexaScriptCompiler compiler = new NexaScriptCompiler();
        CompiledScript script = compiler.compile("""
                fun square(value) => value * value

                fun sumAll(values) {
                    return values.reduce(fun (acc, item) => acc + item, 0)
                }

                val base = 3
                val mapper = fun (item) => item * base
                val values = [1, 2, 3, 4]
                val mapped = values.map(mapper)
                val filtered = mapped.filter(fun (item) => item > 6)
                val found = filtered.find(fun (item) => item == 9)
                val hasLarge = filtered.some(fun (item) => item >= 12)
                val allPositive = filtered.every(fun (item) => item > 0)
                val total = sumAll(filtered)
                val squared = square(5)
                var trace = []
                values.forEach(fun (item, index) {
                    trace.push(`${index}:${item}`)
                })

                msg.payload = {
                    mapped: mapped,
                    filtered: filtered,
                    found: found,
                    hasLarge: hasLarge,
                    allPositive: allPositive,
                    total: total,
                    squared: squared,
                    trace: trace.join("|")
                }

                send(msg)
                """, "ws-func:flow-func:node-func");

        ScriptExecutionResult result = script.execute(new RuntimeMessage(), runtimeContext());
        RuntimeMessage emitted = result.emittedByPort().get("default").getFirst();
        assertEquals(List.of(3D, 6D, 9D, 12D), emitted.readRawValue("payload.mapped"));
        assertEquals(List.of(9D, 12D), emitted.readRawValue("payload.filtered"));
        assertEquals(9D, emitted.readRawValue("payload.found"));
        assertEquals(true, emitted.readRawValue("payload.hasLarge"));
        assertEquals(true, emitted.readRawValue("payload.allPositive"));
        assertEquals(21D, emitted.readRawValue("payload.total"));
        assertEquals(25D, emitted.readRawValue("payload.squared"));
        assertEquals("0:1|1:2|2:3|3:4", emitted.readRawValue("payload.trace"));
    }

    @Test
    void compileErrorMustContainNexaDiagnostic() {
        NexaScriptCompiler compiler = new NexaScriptCompiler();

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> compiler.compile("val broken = ", "ws-c:flow-c:node-c"));

        assertEquals(true, exception.getMessage().contains("[nexa-script-error]"));
        assertEquals(true, exception.getMessage().contains("phase=compile"));
    }

    private ScriptRuntimeContext runtimeContext() {
        return new ScriptRuntimeContext(
                "ws",
                "flow",
                "node",
                "exec-1",
                Instant.now(),
                Instant.now().plusSeconds(10),
                new ConcurrentHashMap<>());
    }
}
