package nexa.compiler.lang;

import java.util.List;
import java.util.Map;

public sealed interface NexaType permits NexaType.Primitive, NexaType.Array, NexaType.ObjectType, NexaType.Named {
    String displayName();
    record Primitive(String displayName) implements NexaType {}
    record Array(NexaType element) implements NexaType { public String displayName(){ return "ARRAY<"+element.displayName()+">"; } }
    record ObjectType(Map<String,NexaType> fields) implements NexaType {
        public ObjectType { fields = Map.copyOf(fields); }
        public String displayName(){ return "OBJECT{"+String.join(",", fields.keySet())+"}"; }
    }
    record Named(String name, NexaType resolved) implements NexaType { public String displayName(){ return name; } }

    Primitive BOOLEAN=new Primitive("BOOLEAN");
    Primitive INT8=new Primitive("INT8"); Primitive INT16=new Primitive("INT16"); Primitive INT32=new Primitive("INT32"); Primitive INT64=new Primitive("INT64");
    Primitive UINT8=new Primitive("UINT8"); Primitive UINT16=new Primitive("UINT16"); Primitive UINT32=new Primitive("UINT32"); Primitive UINT64=new Primitive("UINT64");
    Primitive FLOAT32=new Primitive("FLOAT32"); Primitive FLOAT64=new Primitive("FLOAT64");
    Primitive STRING=new Primitive("STRING"); Primitive OBJECT=new Primitive("OBJECT"); Primitive VOID=new Primitive("VOID");

    static boolean same(NexaType a,NexaType b){ return a.displayName().equals(b.displayName()); }
    static boolean numeric(NexaType t){ return t instanceof Primitive p && List.of("INT8","INT16","INT32","INT64","UINT8","UINT16","UINT32","UINT64","FLOAT32","FLOAT64").contains(p.displayName()); }
}
