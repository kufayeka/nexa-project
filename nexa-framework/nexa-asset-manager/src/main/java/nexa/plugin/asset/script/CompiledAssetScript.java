package nexa.plugin.asset.script;

import nexa.framework.runtime.domain.scripting.internal.nexa.NexaProgram;

/** Immutable compiled representation of an Asset Manager script. */
public record CompiledAssetScript(String source, NexaProgram program) {
}
