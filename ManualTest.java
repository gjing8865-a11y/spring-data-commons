import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import tools.jackson.databind.ObjectMapper;

import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.web.JsonProjectingMethodInterceptorFactory;
import org.springframework.data.web.JsonPath;
import org.springframework.data.web.ProjectedPayload;

import java.io.ByteArrayInputStream;
import java.util.Optional;

public class ManualTest {

    public static void main(String[] args) {
        // 创建投影工厂
        var objectMapper = new ObjectMapper();
        var mappingProvider = new JacksonMappingProvider(objectMapper);
        var jsonProvider = new JacksonJsonProvider(objectMapper);
        
        var projectionFactory = new SpelAwareProxyProjectionFactory();
        projectionFactory.registerMethodInvokerFactory(
            new JsonProjectingMethodInterceptorFactory(jsonProvider, mappingProvider)
        );
        
        // 测试 JSON 数据
        var json = "{\"firstname\" : \"Dave\", \"address\" : { \"zipCode\" : \"01097\" }}";
        
        // 创建投影
        var projection = projectionFactory.createProjection(
            TestProjection.class, 
            new ByteArrayInputStream(json.getBytes())
        );
        
        // 测试 Optional
        System.out.println("Testing Optional support...");
        System.out.println("getFirstname() present: " + projection.getFirstname().isPresent());
        if (projection.getFirstname().isPresent()) {
            System.out.println("getFirstname() value: " + projection.getFirstname().get());
        }
        
        System.out.println("getZipCode() present: " + projection.getZipCode().isPresent());
        if (projection.getZipCode().isPresent()) {
            System.out.println("getZipCode() value: " + projection.getZipCode().get());
        }
        
        System.out.println("getMissingProperty() present: " + projection.getMissingProperty().isPresent());
        
        System.out.println("\nAll tests completed!");
    }
    
    @ProjectedPayload
    interface TestProjection {
        Optional<String> getFirstname();
        
        @JsonPath("$.address.zipCode")
        Optional<String> getZipCode();
        
        Optional<String> getMissingProperty();
    }
}
