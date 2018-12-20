import wemi.Keys.runDirectory


val NknClient by project {

    projectName set { "NknClient" }
    projectGroup set { "nkn" }
    projectVersion set { "0.1-SNAPSHOT" }

    repositories add { repository("jitpack", "https://jitpack.io") }

    libraryDependencies add { dependency("org.slf4j:slf4j-api:1.7.22") } // Logging backend
    libraryDependencies add { dependency("com.github.Darkyenus:tproll:v1.2.4") } // Logging frontend

    libraryDependencies add { dependency("org.json:json:20180130") } // JSON Parser and generator

    libraryDependencies add { dependency("org.java-websocket:Java-WebSocket:1.3.8") } // Websockets
    libraryDependencies add { dependency("com.github.Darkyenus:DaveWebb:v1.2") } // Rest API
    libraryDependencies add { dependency("com.google.protobuf:protobuf-java:3.6.1") } // Proto-buffer implementation

    libraryDependencies add { dependency("org.bouncycastle:bcprov-jdk15on:1.60") } // Crypto

    extend(testing) {
        libraryDependencies add { JUnitAPI }
        libraryDependencies add { JUnitEngine }
    }

    mainClass set { "jsmith.nknclient.examples.MulticastEx" }
    runDirectory set { projectRoot.get() }

}