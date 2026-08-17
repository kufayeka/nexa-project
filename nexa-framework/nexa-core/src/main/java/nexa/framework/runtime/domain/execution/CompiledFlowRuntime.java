package nexa.framework.runtime.domain.execution;

import nexa.framework.runtime.api.NexaCompiledNode;
import nexa.framework.runtime.api.NexaExecutionContext;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.api.state.TypedTagStore;
import nexa.framework.tags.TagQuality;
import nexa.framework.tags.TagRuntime;
import nexa.framework.runtime.domain.deployment.model.CompiledFlow;
import nexa.framework.runtime.domain.deployment.model.CompiledNode;
import nexa.framework.runtime.domain.workspace.model.NodeCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class CompiledFlowRuntime {
    private final CompiledFlow flow;
    private final TypedTagStore legacyTagStore;
    private final TagRuntime tagRuntime;
    private final Map<String, Consumer<RuntimeMessage>> outputHandlers = new ConcurrentHashMap<>();

    public CompiledFlowRuntime(CompiledFlow flow) { this(flow, (TypedTagStore) null); }
    public CompiledFlowRuntime(CompiledFlow flow, TypedTagStore tagStore) { this.flow=Objects.requireNonNull(flow); legacyTagStore=tagStore; tagRuntime=null; }
    public CompiledFlowRuntime(CompiledFlow flow, TagRuntime tagRuntime) { this.flow=Objects.requireNonNull(flow); legacyTagStore=null; this.tagRuntime=tagRuntime; }
    public CompiledFlow flow(){return flow;}
    public void registerOutput(String nodeId,Consumer<RuntimeMessage> handler){outputHandlers.put(Objects.requireNonNull(nodeId),Objects.requireNonNull(handler));}
    public void trigger(String inputNodeId,RuntimeMessage message){requireMessage(message);CompiledNode input=requireNode(inputNodeId);if(input.category()!=NodeCategory.INPUT)throw new IllegalArgumentException("Node "+inputNodeId+" is not an INPUT node");if(!input.enabled())return;dispatch(input.id(),"default",message);}
    private void dispatch(String sourceNodeId,String sourcePort,RuntimeMessage message){List<String> targets=flow.targets(sourceNodeId,sourcePort);for(String targetNodeId:targets){if(!flow.connectionEnabled(sourceNodeId,sourcePort,targetNodeId))continue;CompiledNode target=requireNode(targetNodeId);if(!target.enabled())continue;execute(target,targets.size()>1?message.deepCopy():message);}}
    private void execute(CompiledNode node,RuntimeMessage message){switch(node.category()){case INPUT->dispatch(node.id(),"default",message);case EXECUTOR->executeCompiled(node,message);case OUTPUT->{Consumer<RuntimeMessage> handler=outputHandlers.get(node.id());if(handler==null)throw new IllegalStateException("No output handler registered for node "+node.id());handler.accept(message);}}}
    private void executeCompiled(CompiledNode node,RuntimeMessage message){NexaCompiledNode executable=node.executableNode();if(executable==null)throw new IllegalStateException("Executor node "+node.id()+" has no compiled executable");executable.execute(message,new RuntimeExecutionContext(node.id()));}

    private final class RuntimeExecutionContext implements NexaExecutionContext {
        private final String sourceNodeId; private RuntimeExecutionContext(String sourceNodeId){this.sourceNodeId=sourceNodeId;}
        @Override public void send(RuntimeMessage msg){send("default",msg);}
        @Override public void send(String port,RuntimeMessage msg){requireMessage(msg);dispatch(sourceNodeId,port,msg);}
        @Override public void send(List<String> ports,RuntimeMessage msg){requireMessage(msg);for(String port:new ArrayList<>(ports))send(port,msg);}
        @Override public Object callHostCapability(String namespace,String name,List<Object> args){throw new UnsupportedOperationException("Host capability is not available in the minimal runtime: "+namespace+"."+name);}
        @Override public int readTagInt(int slot){return tagRuntime!=null?tagRuntime.readInt(slot):legacyTagStore.readInt(slot);}
        @Override public long readTagLong(int slot){return tagRuntime!=null?tagRuntime.readLong(slot):legacyTagStore.readLong(slot);}
        @Override public double readTagDouble(int slot){return tagRuntime!=null?tagRuntime.readDouble(slot):legacyTagStore.readDouble(slot);}
        @Override public Object readTagObject(int slot){return tagRuntime!=null?tagRuntime.readSlot(slot):legacyTagStore.readObject(slot);}
        @Override public void writeTagInt(int slot,int value){write(slot,value);}
        @Override public void writeTagLong(int slot,long value){write(slot,value);}
        @Override public void writeTagDouble(int slot,double value){write(slot,value);}
        @Override public void writeTagObject(int slot,Object value){write(slot,value);}
        private void write(int slot,Object value){if(tagRuntime!=null){tagRuntime.writeSlot(slot,value,TagQuality.GOOD);return;}if(value instanceof Integer i)legacyTagStore.writeInt(slot,i);else if(value instanceof Long l)legacyTagStore.writeLong(slot,l);else if(value instanceof Double d)legacyTagStore.writeDouble(slot,d);else legacyTagStore.writeObject(slot,value);}
    }
    private CompiledNode requireNode(String nodeId){CompiledNode node=flow.node(nodeId);if(node==null)throw new IllegalArgumentException("Unknown node id "+nodeId);return node;}
    private void requireMessage(RuntimeMessage message){Objects.requireNonNull(message,"message must not be null");}
}
