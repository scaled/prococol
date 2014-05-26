# Scaled Prococol (Process Protocol) Library

This library simplifies the process of communicating with external processes via stdin/stdout. It
defines a basic text-based protocol for sending framed messages to and from a helper process.

Scaled uses this to talk to compilers, test harnesses and any other external processes that are
designed to interoperate with Scaled modes and services. For situations where Scaled is interacting
with tools that were not designed for use by Scaled, it uses the traditional stone age tools (an
expect-like API and regular expressions).

## Protocol

The protocol is based on the exchange of a series of UTF-8 encoded lines. Lines are separated by the
platform line separator. Prococol data is not intended for inter-machine communication, nor storage
and retrieval. It is intended for communication between two processes on the same machine which
share the same set of operating-system cultural assumptions.

The lines are packaged up into framed messages, and each message contains a simple set of key/value
pairs. A key should appear only once in a given message. If it appears multiple times,
implementations should use the last value they see for the key. If more complex data structures are
needed, they can be encoded in a `%TXT` value, either in another text-based format like JSON, or as
base64 encoded data (if tight coupling between client and server is not an issue).

A Prococol message has the following form:

    %MSG string
    (one or more KEY/XXX pairs)
    %ENDMSG

`string` is a UTF-8 string containing no line separators, but otherwise with no required structure.
Exactly one space character separates `%MSG` from the start of the string.

A KEY/XXX pair is one of:

    %KEY string
    %STR string

where `string` has the same definition as above, and exactly one space separates `%KEY` from its
string payload and `%STR` from its string payload. Or:

    %KEY string
    %TXT
    text
    %ENDTXT

where `string` has the same definition as above, and `text` is unstructured UTF-8 text which may
contain line separators. If the character `% appears at the start of any line of `text` it will be
escaped as `\%`. If the string `\%` appears at the start of any line of `text` it will be escaped as
`\\%`. And so forth. It's turtles all the way down. `%TXT` and `%ENDTXT` are the sole contents of
their lines.

There is no protocol-level mechanism for terminating the communication between client and server. An
orderly closure of the pipes/streams that form the basis of communication is used to accomplish
termination of a session. A particular client/server pair can exchange application level messages to
initiate said closure from the client or server.

## Errata

Prococol is pronounced "prock-o-call".

## Distribution

Scaled JUnit Runner is released under the New BSD License. The most recent version of the code is
available at http://github.com/scaled/junit-runner

[JUnit]: http://junit.org
[Scaled]: https://github.com/scaled/scaled
