plugins {
    `java-library`
    `maven-publish`
}

tasks.withType<JavaCompile> {
    options.release.set(17)
}

publishing {
    publications {
        create<MavenPublication>("release") {
            artifactId = "tonkit-tweetnacl"
            from(components["java"])
        }
    }
}
