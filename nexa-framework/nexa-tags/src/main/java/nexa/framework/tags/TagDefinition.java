package nexa.framework.tags;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record TagDefinition(
        String name,
        TagDataType dataType,
        Object value,
        boolean highSpeed,
        TagCalculationDefinition calculationConfig
) {
    @JsonCreator
    public TagDefinition(@JsonProperty("name") String name,@JsonProperty("dataType") TagDataType dataType,@JsonProperty("value") Object value,@JsonProperty("highSpeed") Boolean highSpeed,@JsonProperty("calculationConfig") TagCalculationDefinition calculationConfig){
        this.name=name;this.dataType=dataType==null?TagDataType.OBJECT:dataType;this.value=value;this.highSpeed=highSpeed!=null&&highSpeed;this.calculationConfig=calculationConfig;
    }
    public TagDefinition(String name,TagDataType dataType,Object value){this(name,dataType,value,false,null);}
    public TagDefinition(String name,TagDataType dataType,Object value,boolean highSpeed){this(name,dataType,value,highSpeed,null);}
}
