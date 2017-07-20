# cmd

A library for Java 8 to run a shell command easily on Unix platforms. 

Creating a program that executes a shell command from Java is a tedious task but 
there are a lot of pitfalls which we almost always fall whenever we write a program 
to use ```Runtime#exec()`` method.

This library does it well on behalf of you. 

To run '''echo hello''', you can simply do either

```java

  public class BasicExample {
    public void echoLocally() { 
      Cmd.cmd("echo hello").stream().forEach(System.out::println);
    }

    // or

    public void echoLocallyWithExplicitShell() { 
      Cmd.cmd(Shell.local(), "echo hello").stream().forEach(System.out::println);
    }
  }

```

Examples above will print out a string ```echo``` to ```stdout```.

To do it over ```ssh```, do

```java

  public class SshExample {
    public void echoRemotely() { 
      Cmd.cmd(Shell.ssh("yourName", "yourHost"), "echo hello").stream().forEach(System.out::println);
    }
  }
```

If you want to specity an identity file (ssh key), you can do

```java
  public class SshExample {
    public void echoRemotelyWithIdentityFile() { 
      Cmd.cmd(Shell.ssh("yourName", "yourHost", "/home/yourName/.ssh/id_rsa"), "echo hello").connect().forEach(System.out::println);
    }
  }
```

Enjoy.

# Installation

**cmd** requires Java SE8 or later. Following is a maven coordinate for it. 

```xml

  <dependency>
    <groupId>com.github.dakusui</groupId>
    <artifactId>cmdstreamer</artifactId>
    <version>[0.9.0,)</version>
  </dependency>
```

# More examples

## Redirection
You can pipe commands not only using ```|``` in command line string but also using
```connectTo``` method. This allows you to make your command line string 
structured and programmable.

```java

    public class PipeExample {
      public void pipe() {
        Cmd.cmd("echo hello && echo world").connectTo(
            Cmd.cmd("cat -n").connectTo(
                Cmd.cmd("sort -r").connectTo(
                    Cmd.cmd("sed 's/hello/HELLO/'").connectTo(
                        Cmd.cmd("sed -E 's/^ +//'")
                    )))
        ).stream(
        ).map(
            s -> String.format("<%s>", s)
        ).forEach(
            System.out::println
        );
      }
    }

```

The example above will print something like following.

```

<2	world>
<1	HELLO>
```

## Tee
You can fork an output from a command into some like a unix command ```tee```.

```java

    public class TeeExample {
      public void tee10K() {
        Cmd.cmd(
            "seq 1 10000"
        ).readFrom(
            () -> Stream.of((String) null)
        ).connectTo(
            Cmd.cat().pipeline(
                stream -> stream.map(
                    s -> "LEFT:" + s
                )
            ),
            Cmd.cat().pipeline(
                stream -> stream.map(
                    s -> "RIGHT:" + s
                )
            )
        ).stream(
        ).forEach(
            System.out::println
        );
      }
    }
    
```

## Compatibility with ```commandrunner``` library
This library is designed to be compatible with ```commandrunner```[[0]] library.
By using ```CommandUtils```, you can replace your dependency on ```commandrunner```.

```java

  public class CommandUtilsExample {
      public void runLocal_echo_hello() throws Exception {
        CommandResult result = CommandUtils.runLocal("echo hello");
        // ...
      }
  }

```

# Future works
* Implement a timeout feature

# References
* [0] "commandrunner"

[0]: https://github.com/xjj59307/commandrunner


