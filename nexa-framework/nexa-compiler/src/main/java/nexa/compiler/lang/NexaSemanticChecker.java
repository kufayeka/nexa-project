package nexa.compiler.lang;

import java.util.*;
import static nexa.compiler.lang.NexaAst.*;

/** Final Workstream-1 semantic checker. OBJECT is the dynamic object supertype; all primitive conversions remain strict. */
public final class NexaSemanticChecker {
 public record Diagnostic(String message,SourceSpan span){}
 private final Map<String,NexaType> types=new HashMap<>(); private final Deque<Map<String,NexaType>> scopes=new ArrayDeque<>(); private final List<Diagnostic> errors=new ArrayList<>();
 public NexaSemanticChecker(){for(NexaType t:List.of(NexaType.BOOLEAN,NexaType.INT8,NexaType.INT16,NexaType.INT32,NexaType.INT64,NexaType.UINT8,NexaType.UINT16,NexaType.UINT32,NexaType.UINT64,NexaType.FLOAT32,NexaType.FLOAT64,NexaType.STRING,NexaType.OBJECT,NexaType.VOID))types.put(t.displayName(),t);}
 public List<Diagnostic> check(Program p){errors.clear();scopes.clear();scopes.push(new HashMap<>(Map.of("self",NexaType.OBJECT,"input",NexaType.OBJECT)));for(Stmt s: p.statements())stmt(s);return List.copyOf(errors);}
 private void stmt(Stmt s){
  if(s instanceof TypeDecl d){if(types.containsKey(d.name()))bad("Type already defined: "+d.name(),d.span());else types.put(d.name(),resolve(d.type()));return;}
  if(s instanceof Let l){NexaType v=expr(l.init());require(resolve(l.type()),v,l.init(),"initializer");define(l.name(),resolve(l.type()));return;}
  if(s instanceof Assign a){if(!lvalue(a.target()))bad("Assignment target is not writable",a.target().span());NexaType t=expr(a.target()),v=expr(a.value());require(t,v,a.value(),"assignment");return;}
  if(s instanceof Return r){expr(r.value());return;} if(s instanceof ExprStmt e){expr(e.expr());return;}
  if(s instanceof For f){NexaType it=expr(f.iterable());if(!(it instanceof NexaType.Array a))bad("'in' requires ARRAY<T>",f.iterable().span());else{require(resolve(f.declaredType()),a.element(),f.iterable(),"loop variable");scopes.push(new HashMap<>());define(f.name(),resolve(f.declaredType()));for(Stmt b:f.body())stmt(b);scopes.pop();}}
 }
 private NexaType expr(Expr e){
  if(e instanceof Literal l)return l.type();
  if(e instanceof Var v){NexaType t=lookup(v.name());if(t==null){bad("Unknown variable: "+v.name(),v.span());return NexaType.OBJECT;}return resolve(t);}
  if(e instanceof Field f){NexaType t=resolve(expr(f.target()));if(t instanceof NexaType.ObjectType o){NexaType v=o.fields().get(f.name());if(v==null){bad("Unknown field '"+f.name()+"'",f.span());return NexaType.OBJECT;}return resolve(v);}if(NexaType.same(t,NexaType.OBJECT))return NexaType.OBJECT;bad("Field access requires OBJECT/struct",f.span());return NexaType.OBJECT;}
  if(e instanceof Index i){NexaType t=resolve(expr(i.target()));NexaType idx=expr(i.index());if(t instanceof NexaType.Array a){require(NexaType.INT64,idx,i.index(),"array index");return a.element();}if(NexaType.same(t,NexaType.OBJECT))return NexaType.OBJECT;bad("Indexing requires ARRAY or OBJECT",i.span());return NexaType.OBJECT;}
  if(e instanceof Array a){NexaType et=null;for(Expr x:a.values()){NexaType xt=expr(x);et=et==null?xt:common(et,xt,x.span());}return new NexaType.Array(et==null?NexaType.OBJECT:et);}
  if(e instanceof ObjectLit o){Map<String,NexaType> f=new LinkedHashMap<>();for(var x:o.fields().entrySet())f.put(x.getKey(),expr(x.getValue()));return new NexaType.ObjectType(f);}
  if(e instanceof Unary u){NexaType t=expr(u.expr());if(u.op().equals("!")){require(NexaType.BOOLEAN,t,u.expr(),"logical negation");return NexaType.BOOLEAN;}if(!NexaType.numeric(t))bad("Unary numeric operator requires numeric value",u.span());return t;}
  if(e instanceof Binary b){NexaType l=expr(b.left()),r=expr(b.right());if(b.op().equals("&&")||b.op().equals("||")){require(NexaType.BOOLEAN,l,b.left(),"logical operand");require(NexaType.BOOLEAN,r,b.right(),"logical operand");return NexaType.BOOLEAN;}if(Set.of("==","!=","<","<=",">",">=").contains(b.op())){if(!compatible(l,r))bad("Incompatible comparison types",b.span());return NexaType.BOOLEAN;}if(!NexaType.numeric(l)||!NexaType.numeric(r))bad("Arithmetic requires numeric operands",b.span());return common(l,r,b.span());}
  if(e instanceof Call c){for(Expr a:c.args())expr(a);return NexaType.OBJECT;}throw new IllegalStateException("Unknown expression");
 }
 private NexaType common(NexaType a,NexaType b,SourceSpan s){a=resolve(a);b=resolve(b);if(NexaType.same(a,b))return a;if(NexaType.numeric(a)&&NexaType.numeric(b)){if(a.displayName().equals("FLOAT64")||b.displayName().equals("FLOAT64"))return NexaType.FLOAT64;if(a.displayName().equals("FLOAT32")||b.displayName().equals("FLOAT32"))return NexaType.FLOAT32;return rank(a)>=rank(b)?a:b;}bad("No compatible types: "+a.displayName()+" and "+b.displayName(),s);return NexaType.OBJECT;}
 private boolean compatible(NexaType a,NexaType b){return NexaType.same(resolve(a),resolve(b))||(NexaType.numeric(a)&&NexaType.numeric(b));}
 private void require(NexaType target,NexaType value,Expr source,String where){target=resolve(target);value=resolve(value);if(NexaType.same(target,value)|| (NexaType.same(target,NexaType.OBJECT)&&value instanceof NexaType.ObjectType))return;if(source instanceof Literal l&&literalFits(l,target))return;if(NexaType.numeric(target)&&NexaType.numeric(value)&&rank(value)<=rank(target)&&!(value.displayName().startsWith("UINT")&&!target.displayName().startsWith("UINT")))return;bad("Type mismatch in "+where+": expected "+target.displayName()+", got "+value.displayName(),source.span());}
 private boolean literalFits(Literal l,NexaType t){if(!(l.value() instanceof Number n)||!NexaType.numeric(t))return false;double v=n.doubleValue();return switch(t.displayName()){case "INT8"->v>=-128&&v<=127;case "INT16"->v>=-32768&&v<=32767;case "INT32"->v>=Integer.MIN_VALUE&&v<=Integer.MAX_VALUE;case "INT64"->true;case "UINT8"->v>=0&&v<=255;case "UINT16"->v>=0&&v<=65535;case "UINT32"->v>=0&&v<=4294967295d;case "UINT64"->v>=0;case "FLOAT32","FLOAT64"->Double.isFinite(v);default->false;};}
 private int rank(NexaType t){return switch(t.displayName()){case "INT8","UINT8"->1;case "INT16","UINT16"->2;case "INT32","UINT32"->3;case "INT64","UINT64"->4;case "FLOAT32"->5;case "FLOAT64"->6;default->0;};}
 private NexaType resolve(NexaType t){if(t instanceof NexaType.Named n){NexaType r=types.get(n.name());if(r==null){bad("Unknown type: "+n.name(),new SourceSpan(0,0));return NexaType.OBJECT;}return r;}if(t instanceof NexaType.Array a)return new NexaType.Array(resolve(a.element()));if(t instanceof NexaType.ObjectType o){Map<String,NexaType> f=new LinkedHashMap<>();o.fields().forEach((k,v)->f.put(k,resolve(v)));return new NexaType.ObjectType(f);}return t;}
 private boolean lvalue(Expr e){return e instanceof Var||e instanceof Field||e instanceof Index;}private void define(String n,NexaType t){scopes.peek().put(n,t);}private NexaType lookup(String n){for(var s:scopes)if(s.containsKey(n))return s.get(n);return null;}private void bad(String m,SourceSpan s){errors.add(new Diagnostic(m,s));}
}
