# Flash
Flash is a high-performance web framework for Java 21+, designed with implementation efficiency in mind. Unlike many popular server frameworks, initialization is performed ahead of time to minimize runtime overhead.

Routing is handled through a compressed trie (radix tree) for fast endpoint resolution. Request handling is multithreaded, with pooled handler instances that scale dynamically to improve concurrency and reduce memory allocation. Flash is entirely type-safe, zero-reflection and zero-dependency.

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
public class HelloWorld {
    private final int PORT = 8080;
    public static void main(String[] args) {
        FlashServer server = new FlashServer(PORT);

        //Defines a simple GET route.
        server.get("/hello", (req, res) -> "Hello, World!");

        server.start();
    }
}
```

----

## Documentation
Documentation is available on the official [PixelDocs page](https://docs.pixel-services.com/flash/), Javadocs can be found [here](https://flash.pixel-services.com/javadoc/).

----

## Contributing
We welcome contributions! To contribute to Flash:
1. Fork the repository: [Flash on GitHub](https://github.com/Relism/flash)
2. Create a feature branch: `git checkout -b feature-name`
3. Commit your changes: `git commit -m 'Add feature'`
4. Push to the branch: `git push origin feature-name`
5. Submit a pull request.
