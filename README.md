# NKN SDK for Java/Kotlin/JVM


Java implementation of [NKN](https://github.com/nknorg/nkn/) sdk.

Send and receive messages between any NKN clients.

__This is very work in progress, everything can change!__


## Where to start?

There is [wiki documentation](https://github.com/RealJohnSmith/nkn-java-sdk/wiki) of the client.

You can also have a look at the [examples](examples/src/main/java/jsmith/nknskd/examples) for basic introduction to API. Explore classes mentioned in examples


### How to try?

* Install java8 or bigger
* Clone repository to a local folder
* Type `./wemi <ExampleName>/run`

Simple as that.

Substitute `<ExampleName>` for any of the prepared examples:
 * `SimpleExample` Contains simple unicast message send and receive, response; with an option to opt-out of the end2end encryption scheme.
 * `DropBenchmarkExample` Leftover test of message drops, from ancient times when sending messages was a lot less reliable
 * `MulticastExample` Broadcast messages to multiple clients at the same time, including ACK/Response handling
 * `WalletExample` Demonstration of generating, saving and loading a wallet. Including explorer queries for balance and wallet transaction to register a name or transfer assets to a different wallet
 * `PubSubExample` Sub transactions, pub message broadcast and receiving.


For more information about `wemi` build system, visit GitHub page: [Darkyenus/WEMI](https://github.com/Darkyenus/wemi) 



## Contributions

__Can I submit a bug, suggestion or feature request?__

Yes. Please open an issue for that.

__Can I contribute code to NKN-java-sdk project?__

Yes please, we appreciate your help! To make contributions, please fork the repo, push your changes to the forked repo, and open a pull request here.
