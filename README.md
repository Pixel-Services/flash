# Flash
**Flash** is a lightweight and expressive web framework for Java. It is a fork of the popular [Spark Framework](https://github.com/perwendel/spark),
designed to offer enhanced performance, additional features, and a modernized development experience.

Flash is perfect for building RESTful APIs and simple web applications. Its minimalist design ensures
ease of use while maintaining flexibility and power for more advanced needs.

----

## Installation
To include Flash in your project, add the following dependency and repository to your ``pom.xml``:

### Dependency
```xml
<dependency>
    <groupId>com.pixelservices</groupId>
    <artifactId>flash</artifactId>
    <version>${flashversion}</version>
</dependency>
```
### Repository
```xml
<repository>
    <id>pixel-services-releases</id>
    <name>Pixel Services</name>
    <url>https://maven.pixel-services.com/releases</url>
</repository>
```

----

## Quick Start
Here’s a simple example of a Flash application:

```java
import static flash.FlashServerHelper.*;

public class HelloWorld {
    public static void main(String[] args) {
        get("/hello", (req, res) -> "Hello, World!");
    }
}
```

----

## Documentation
Flash maintains compatibility with most of [Spark's API](https://javadoc.io/doc/com.sparkjava/spark-core) and documentation.
To get started, you can refer to the Spark Documentation. For features specific to Flash, check out our project repository:
[Flash on GitHub](https://github.com/Pixel-Services/flash).

----

## Contributing
We welcome contributions! To contribute to Flash:
1. Fork the repository: [Flash on GitHub](https://github.com/Pixel-Services/flash)
2. Create a feature branch: `git checkout -b feature-name`
3. Commit your changes: `git commit -m 'Add feature'`
4. Push to the branch: `git push origin feature-name`
5. Submit a pull request.

----

## Acknowledgments
Flash is a fork of the [Spark Framework](https://github.com/perwendel/spark).
We extend our gratitude to the Spark community for their foundational work and inspiration.

----
Start building with Flash today! 🚀
