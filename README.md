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
    }

    public static void main(String[] args) {
        Foo foo = new Foo();
        foo.bar(); // print "hello"
        foo.bar(); // not print
        foo.bar(); // not print
    }
}
```