package fi.nls.oskari.routing;

import com.fasterxml.jackson.annotation.*;

import javax.annotation.Generated;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("org.jsonschema2pojo")
@JsonPropertyOrder({
        "x",
        "y"
})
public class Shape {

    @JsonProperty("x")
    private Integer x;
    @JsonProperty("y")
    private Double y;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     *
     * @return
     * The x
     */
    @JsonProperty("x")
    public Integer getX() {
        return x;
    }

    /**
     *
     * @param x
     * The x
     */
    @JsonProperty("x")
    public void setX(Integer x) {
        this.x = x;
    }

    /**
     *
     * @return
     * The y
     */
    @JsonProperty("y")
    public Double getY() {
        return y;
    }

    /**
     *
     * @param y
     * The y
     */
    @JsonProperty("y")
    public void setY(Double y) {
        this.y = y;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
