import wemi.Keys.runDirectory


val NknSdk by project {

    projectName set { "NknSdk" }
    projectGroup set { "cz.jsmith.nkn" }
    projectVersion set { "0.1-SNAPSHOT" }

    repositories add { repository("jitpack", "https://jitpack.io") }

    libraryDependencies add { dependency("org.slf4j:slf4j-api:1.7.22") } // Logging backend

    libraryDependencies add { dependency("org.json:json:20180130") } // JSON Parser and generator

    libraryDependencies add { dependency("org.java-websocket:Java-WebSocket:1.3.8") } // Websockets
    libraryDependencies add { dependency("com.github.Darkyenus:DaveWebb:v1.2") } // Rest API
    libraryDependencies add { dependency("com.google.protobuf:protobuf-java:3.6.1") } // Proto-buffer implementation

    libraryDependencies add { dependency("org.bouncycastle:bcprov-jdk15on:1.60") } // Crypto


}

val NknSdkExample by project {

    projectDependencies add { dependency(NknSdk) }

    repositories add { repository("jitpack", "https://jitpack.io") }
    libraryDependencies add { dependency("com.github.Darkyenus:tproll:v1.2.4") } // Logging frontend



    projectRoot set { path("examples") }

    extend(compilingJava){
        sourceRoots set { setOf(projectRoot.get() / "src") }
    }
    extend(compilingKotlin){
        sourceRoots set { setOf() }
    }
    resourceRoots set { setOf() }
    extend(testing) {
        resourceRoots set { setOf() }
    }


    mainClass set { "jsmith.nknsdk.examples.SimpleEx" }

    runDirectory set { projectRoot.get() }

}