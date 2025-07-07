# Scalus Starter Project

This is a starter project for [Scalus](https://scalus.org) -
Scala-based DApps development platform for Cardano.

It contains a simple example of a Minting Policy script written using Scalus.

## Getting Started

### Prerequisites

You'll need Java JDK 11+, [scala-cli](https://scala-cli.virtuslab.org/) or [sbt](https://www.scala-sbt.org/).

If you use Nix, you can run `nix develop` to get a shell with all the dependencies.

### Setup IntelliJ IDEA

File -> New -> Project from Existing Sources -> select the project directory -> Import project from external model ->
BSP -> Sbt

### Building with sbt

Run `sbt` to enter the sbt shell.

Run `compile` to compile the project.

Run `test` to run the tests.

Run `integration/test` to run the integration tests.

Run `scalafmtAll` to format the code.

### Setup Yaci Devkit for integration tests

Follow instructions from the [Yaci Devkit](https://devkit.yaci.xyz/) to setup the devkit.

Create and run a local Cardano node with the devkit:

```shell
devkit
> create-node
> start
```

#### Resetting the local Cardano node

If you need to reset the local Cardano node, you can run:

```shell
reset
```

in the devkit shell.

## Scalus Getting Started

[Read the tutorial](https://scalus.org/docs/get-started)

## Resources

Scalus official website: <https://scalus.org>

Find more information about Scalus in the [Scalus repository](https://github.com/nau/scalus).

Scalus Discord: <https://discord.gg/ygwtuBybsy>

Twitter: [@Scalus3](https://twitter.com/Scalus3)
