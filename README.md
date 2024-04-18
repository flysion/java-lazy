
```xml
<dependency>
    <groupId>com.github.flysion</groupId>
    <artifactId>lazy</artifactId>
    <version>1.0.4-SNAPSHOT</version>
</dependency>
```

## @Once

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