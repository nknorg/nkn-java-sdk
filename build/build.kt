import wemi.Keys.runDirectory
import wemi.*
import wemi.dependency.Jitpack
import wemi.dependency.ProjectDependency

val NknSdk by project {

    projectName set { "NknSdk" }
    projectGroup set { "cz.jsmith.nkn" }
    projectVersion set { "0.1-SNAPSHOT" }

    repositories add { Jitpack }

    libraryDependencies add { dependency("org.slf4j:slf4j-api:1.7.22") } // Logging backend

    libraryDependencies add { dependency("org.json:json:20180130") } // JSON Parser and generator

//    libraryDependencies add { dependency("org.java-websocket:Java-WebSocket:1.3.8") } // Websockets
    libraryDependencies add { dependency("javax.websocket:javax.websocket-client-api:1.0") } // Websockets
    libraryDependencies add { dependency("org.glassfish.tyrus:tyrus-client:1.1") }
    libraryDependencies add { dependency("org.glassfish.tyrus:tyrus-container-grizzly:1.1") }
//    libraryDependencies add { dependency("org.eclipse.jetty.websocket:websocket-client:9.4.15.v20190215") } // Websockets


    libraryDependencies add { dependency("com.github.Darkyenus:DaveWebb:v1.2") } // Rest API
    libraryDependencies add { dependency("com.google.protobuf:protobuf-java:3.6.1") } // Proto-buffer implementation

    libraryDependencies add { dependency("net.i2p.crypto:eddsa:0.3.0") } // Crypto, Ed25519
    libraryDependencies add { dependency("org.bouncycastle:bcprov-jdk15on:1.61") } // Crypto, The rest


}

val SimpleExample by project(path("examples")) {

    projectDependencies add { ProjectDependency(NknSdk, false) }

    repositories add { Jitpack }
    libraryDependencies add { dependency("com.github.Darkyenus:tproll:v1.3.0") } // Logging frontend

    mainClass set { "jsmith.nknsdk.examples.SimpleEx" }

    runDirectory set { projectRoot.get() }

}

val MulticastExample by project(path("examples")) {

    projectDependencies add { ProjectDependency(NknSdk, false) }

    repositories add { Jitpack }
    libraryDependencies add { dependency("com.github.Darkyenus:tproll:v1.3.1") } // Logging frontend

    mainClass set { "jsmith.nknsdk.examples.MulticastEx" }

    runDirectory set { projectRoot.get() }

}


val WalletExample by project(path("examples")) {

    projectDependencies add { ProjectDependency(NknSdk, false) }

    repositories add { Jitpack }
    libraryDependencies add { dependency("com.github.Darkyenus:tproll:v1.3.1") } // Logging frontend

    mainClass set { "jsmith.nknsdk.examples.WalletEx" }

    runDirectory set { projectRoot.get() }

}