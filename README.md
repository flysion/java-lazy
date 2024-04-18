```xml

<dependency>
    <groupId>com.github.flysion</groupId>
    <artifactId>lazy</artifactId>
    <version>1.0.4-SNAPSHOT</version>
</dependency>
```

```xml

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.10.1</version>
            <configuration>
                <source>1.8</source>
                <target>1.8</target>
                <encoding>UTF-8</encoding>
                <annotationProcessorPaths>
                    <path>
                        <groupId>com.github.flysion</groupId>
                        <artifactId>lazy</artifactId>
                        <version>${lazy.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### @Once

Let the code be executed only once

```java
import com.github.flysion.lazy.annotation.Once;

public class Foo {
    @Once
    public String bar() {
        System.out.println("hello");
        return "123";
    }

    public static void main(String[] args) {
        Foo foo = new Foo();
        String a = foo.bar(); // a= "123" and print "hello"
        String b = foo.bar(); // b= "123" and not print
        String c = foo.bar(); // c= "123" and not print
    }
}
```