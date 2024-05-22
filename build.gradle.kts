import groovy.json.JsonSlurper
import org.apache.tools.ant.util.TeeOutputStream
import java.io.ByteArrayOutputStream


// region Paths, directories and file names

val infrastructureDirectoryPath = rootDir.path + File.separator + "terraform"
val infrastructureDataFilePath = buildDir.path + File.separator + "infrastructure_state.json"
val componentsDirectoryPath = rootDir.path + File.separator + "components"
val vpnServerDataFilePath = buildDir.path + File.separator + "vpn_server_data"
val privateInstanceDataFilePath = buildDir.path + File.separator + "private_instance_data"
val publicInstanceDataFilePath = buildDir.path + File.separator + "public_instance_data"
val openVpnDirectoryPath = componentsDirectoryPath + File.separator + "openvpn"
val openVpnSetupDirectoryName = "openvpn_setup"
val openVpnSetupFileDataPath = buildDir.path + File.separator + "openvpn_status"
val vpnServerUser = "ubuntu"
val observabilityDirectoryPath = componentsDirectoryPath + File.separator + "observability"
val observabilityDataPath = observabilityDirectoryPath + File.separator + "read_path" + File.separator + "data"
val otelCollectorDirectoryPath = observabilityDirectoryPath + File.separator + "otel_collector"
val otelCollectorImageVersion = "1.0"
val otelCollectorImageName = "otel-collector:$otelCollectorImageVersion"
val nginxDirectoryPath = componentsDirectoryPath + File.separator + "nginx"
val nginxImageVersion = "1.0"
val nginxImageName = "nginx:$nginxImageVersion"

// endregion

// region Infrastructure deployment

val deployInfrastructure by tasks.registering {
    group = "deployment"
    description = "Deploy infrastructure with terraform. Changes are automatically applied"
    outputs.file(infrastructureDataFilePath)

    doLast {
        exec {
            workingDir = File(infrastructureDirectoryPath)
            commandLine("terraform")
            args("apply", "-auto-approve")
        }
        val stdOut = ByteArrayOutputStream()
        exec {
            workingDir = File(infrastructureDirectoryPath)
            standardOutput = stdOut
            commandLine("terraform")
            args("show", "-json")
        }
        File(infrastructureDataFilePath)
            .writer()
            .use { it.write(stdOut.toString()) }
    }
}

val destroyInfrastructure by tasks.registering {
    group = "deployment"
    description = "Destroy infrastructure with terraform. Changes are automatically applied"

    doLast {
        exec {
            workingDir = File(infrastructureDirectoryPath)
            commandLine("terraform")
            args("apply", "-destroy", "-auto-approve")
        }
        delete(buildDir)
    }
}

// endregion

// region VPN

val extractVpnServerUrl by tasks.registering {
    group = "vpn"
    description = "Extract VPN server public ip address"
    inputs.files(deployInfrastructure.get().outputs.files)
    outputs.file(vpnServerDataFilePath)

    doLast {
        val serverInfo =
            (JsonSlurper()
                .parseText(File(infrastructureDataFilePath).reader().readText()) as Map<*, *>)
                .let { it["values"] as Map<*, *> }
                .let { it["root_module"] as Map<*, *> }
                .let { it["resources"] as List<*> }
                .find { with(it as Map<*, *>) { this["type"] == "aws_eip" && this["name"] == "vpn_nat" } }
                .let { it as Map<*, *> }
                .let { it["values"] as Map<*, *> }
        val publicUrl = serverInfo["public_ip"]

        File(vpnServerDataFilePath)
            .printWriter()
            .use { it.print(publicUrl) }
    }
}

val pushVpnSetup by tasks.registering {
    group = "vpn"
    description = "Set up OpenVPN server"
    inputs.files(extractVpnServerUrl.get().outputs.files)

    doLast {
        val host = File(vpnServerDataFilePath).reader().readText()

        // Push installation scripts onto the server
        exec {
            workingDir = File(openVpnDirectoryPath)
            commandLine("scp")
            args(
                *sshArgs,
                "-r", openVpnDirectoryPath + File.separator + openVpnSetupDirectoryName,
                "$vpnServerUser@$host:~"
            )
        }
    }
}

val setupVpnServer by tasks.registering {
    group = "vpn"
    description = "Set up OpenVPN server"
    inputs.files(extractVpnServerUrl.get().outputs.files)
    outputs.file(openVpnSetupFileDataPath)

    dependsOn(pushVpnSetup)

    doLast {
        val host = File(vpnServerDataFilePath).reader().readText()

        // Execute installation scripts
        listOf(
            "install_packages.sh",
            "setup_ip4_forwarding.sh",
            "setup_ip_tables.sh",
            "setup_keys_and_configs.sh"
        ).forEach { script ->
            exec {
                commandLine("ssh")
                args(
                    *sshArgs,
                    "$vpnServerUser@$host",
                    "sudo sh ~/$openVpnSetupDirectoryName/$script"
                )
            }
        }

        // Start OpenVPN
        exec {
            commandLine("ssh")
            args(
                *sshArgs,
                "$vpnServerUser@$host",
                "sudo systemctl start openvpn@server"
            )
        }

        // Store OpenVPN status
        val stdOut = ByteArrayOutputStream()
        exec {
            standardOutput = TeeOutputStream(System.out, stdOut)
            commandLine("ssh")
            args(
                *sshArgs,
                "$vpnServerUser@$host",
                "sudo systemctl status openvpn@server"
            )
        }
        File(openVpnSetupFileDataPath)
            .printWriter()
            .use { it.print(stdOut.toString()) }
    }
}

val createVpnClient by tasks.registering {
    group = "vpn"
    description = "Create OpenVPN client credentials and download them"
    val clientName = "client"
    val openVpnClientCredentialsFilePath = buildDir.path + File.separator + clientName + ".ovpn"

    doLast {
        val host = File(vpnServerDataFilePath).reader().readText()

        // Execute script
        exec {
            commandLine("ssh")
            args(
                *sshArgs,
                "$vpnServerUser@$host",
                "sudo sh ~/$openVpnSetupDirectoryName/create_client.sh $clientName $host"
            )
        }

        // Download client credentials
        exec {
            commandLine("scp")
            args(
                *sshArgs,
                "$vpnServerUser@$host:/etc/openvpn/clients/$clientName.ovpn", openVpnClientCredentialsFilePath
            )
        }
    }
}

// endregion

// region Instances setup

val extractPublicInstanceUrl by tasks.registering {
    group = "instances setup"
    description = "Extract public instance ip address"
    inputs.files(deployInfrastructure.get().outputs.files)
    outputs.file(publicInstanceDataFilePath)

    doLast {
        val serverInfo =
            (JsonSlurper()
                .parseText(File(infrastructureDataFilePath).reader().readText()) as Map<*, *>)
                .let { it["values"] as Map<*, *> }
                .let { it["root_module"] as Map<*, *> }
                .let { it["resources"] as List<*> }
                .find { with(it as Map<*, *>) { this["type"] == "aws_instance" && this["name"] == "public" } }
                .let { it as Map<*, *> }
                .let { it["values"] as Map<*, *> }
        val publicUrl = serverInfo["private_ip"]

        File(publicInstanceDataFilePath)
            .printWriter()
            .use { it.print(publicUrl) }
    }
}

val extractPrivateInstanceUrl by tasks.registering {
    group = "instances setup"
    description = "Extract private instance ip address"
    inputs.files(deployInfrastructure.get().outputs.files)
    outputs.file(privateInstanceDataFilePath)

    doLast {
        val serverInfo =
            (JsonSlurper()
                .parseText(File(infrastructureDataFilePath).reader().readText()) as Map<*, *>)
                .let { it["values"] as Map<*, *> }
                .let { it["root_module"] as Map<*, *> }
                .let { it["resources"] as List<*> }
                .find { with(it as Map<*, *>) { this["type"] == "aws_instance" && this["name"] == "private" } }
                .let { it as Map<*, *> }
                .let { it["values"] as Map<*, *> }
        val publicUrl = serverInfo["private_ip"]

        File(privateInstanceDataFilePath)
            .printWriter()
            .use { it.print(publicUrl) }
    }
}

val installDockerPublicInstance by tasks.registering {
    group = "instances setup"
    description = "Install docker on public instance"
    inputs.files(publicInstanceDataFilePath)

    dependsOn(extractPublicInstanceUrl)

    doLast {
        val host = "ubuntu@" + File(publicInstanceDataFilePath).reader().readText()
        exec {
            // Push docker installation script to remote host
            commandLine("scp")
            args(
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                "scripts${File.separator}install_docker.sh",
                "$host:~"
            )
        }
        exec {
            // Execute docker installation script on remote host
            commandLine("ssh")
            args(
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                host, "sh ~/install_docker.sh"
            )
        }
    }
}

val installDockerPrivateInstance by tasks.registering {
    group = "instances setup"
    description = "Install docker on private instance"
    inputs.files(privateInstanceDataFilePath)

    dependsOn(extractPrivateInstanceUrl)

    doLast {
        val host = "ubuntu@" + File(privateInstanceDataFilePath).reader().readText()
        exec {
            // Push docker installation script to remote host
            commandLine("scp")
            args(
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                "scripts${File.separator}install_docker.sh",
                "$host:~"
            )
        }
        exec {
            // Execute docker installation script on remote host
            commandLine("ssh")
            args(
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                host, "sh ~/install_docker.sh"
            )
        }
    }
}

val runPortainerPublicInstance by tasks.registering {
    group = "instances setup"
    description = "Install portainer on public instance"
    inputs.files(publicInstanceDataFilePath)

    dependsOn(extractPublicInstanceUrl)

    doLast {
        val host = "ubuntu@" + File(publicInstanceDataFilePath).reader().readText()
        exec {
            // Push docker installation script to remote host
            commandLine("scp")
            args(
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                "scripts${File.separator}run_portainer.sh",
                "$host:~"
            )
        }
        exec {
            // Execute docker installation script on remote host
            commandLine("ssh")
            args(
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                host, "sh ~/run_portainer.sh"
            )
        }
    }
}

val runPortainerPrivateInstance by tasks.registering {
    group = "instances setup"
    description = "Install portainer on private instance"
    inputs.files(privateInstanceDataFilePath)

    dependsOn(extractPrivateInstanceUrl)

    doLast {
        val host = "ubuntu@" + File(privateInstanceDataFilePath).reader().readText()
        exec {
            // Push docker installation script to remote host
            commandLine("scp")
            args(
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                "scripts${File.separator}run_portainer.sh",
                "$host:~"
            )
        }
        exec {
            // Execute docker installation script on remote host
            commandLine("ssh")
            args(
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                host, "sh ~/run_portainer.sh"
            )
        }
    }
}

val containerRegistrySetup by tasks.registering {
    group = "instances setup"
    description = "Set up container registry"
    inputs.files(privateInstanceDataFilePath)
    inputs.files(publicInstanceDataFilePath)

    dependsOn(extractPublicInstanceUrl)

    doLast {
        val privateInstanceUrl = File(privateInstanceDataFilePath).reader().readText()
        val host = "ubuntu@" + File(publicInstanceDataFilePath).reader().readText()
        exec {
            // Execute docker installation script on remote host
            commandLine("ssh")
            args(
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                host, "sudo sh -c \"echo '{\\\"insecure-registries\\\" : [\\\"http://$privateInstanceUrl:5000\\\"]}' > /etc/docker/daemon.json\""
            )
        }
    }
}

// endregion

// region Observability setup

val buildOtelCollectorImage by tasks.registering {
    group = "observability setup"
    description = "Builds Open Telemetry Collector docker image"

    doLast {
        exec {
            workingDir = File(otelCollectorDirectoryPath)
            commandLine("docker")
            args("build", "-t", otelCollectorImageName, ".")
        }
    }
}

val pushOtelCollectorImageToRegistry by tasks.registering {
    group = "observability setup"
    description = "Push Open Telemetry Collector docker image to container registry run on private instance"
    inputs.files(privateInstanceDataFilePath)

    doLast {
        val dockerRegistryUrl = File(privateInstanceDataFilePath).reader().readText() + ":5000"

        val imageUrl = "$dockerRegistryUrl/$otelCollectorImageName"
        exec {
            commandLine("docker")
            args("tag", otelCollectorImageName, imageUrl)
        }
        exec {
            commandLine("docker")
            args("push", imageUrl)
        }
    }
}

val fixDataPermissionsAndOwnership by tasks.registering {
    group = "observability setup"
    description = "Set up proper permissions and ownership of data"

    doLast {
        val host = "ubuntu@" + File(privateInstanceDataFilePath).reader().readText()
        exec {
            // Set ownership of loki files to loki user (10001)
            // See: https://github.com/grafana/loki/blob/98551ceb9aca19f2914a96d9b3493b692456c1ce/cmd/loki/Dockerfile#L14-L18
            commandLine("ssh")
            args(
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                host, "sudo chown -R 10001:10001 /tmp/observability/loki-data"
            )
        }
        exec {
            // Set permissions for files in observability so that they can be accessed later on
            commandLine("ssh")
            args(
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                host, "sudo chmod -R 777 /tmp/observability"
            )
        }
    }
}

// endregion

// region Observability operations

val downloadObservabilityData by tasks.registering {
    group = "observability operations"
    description = "Downloads observability data"


    doLast {
        exec {
            // Create directory for observability data (if it doesn't exist)
            commandLine("mkdir")
            args(
                "-p",
                observabilityDataPath
            )
        }

        val host = "ubuntu@" + File(privateInstanceDataFilePath).reader().readText()
        exec {
            // Download observability data with preserving files modification times, access times and modes
            commandLine("scp")
            args(
                "-prv",
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                "$host:/tmp/observability",
                observabilityDataPath,
            )
        }
    }
}

// endregion

// region Reverse proxy setup

val buildNginxImage by tasks.registering {
    group = "reverse proxy setup"
    description = "Builds Nginx docker image"

    doLast {
        exec {
            workingDir = File(nginxDirectoryPath)
            commandLine("docker")
            args("build", "-t", nginxImageName, ".")
        }
    }
}

val pushNginxImageToRegistry by tasks.registering {
    group = "reverse proxy setup"
    description = "Push Nginx docker image to container registry"

    doLast {
        val dockerRegistryUrl = File(privateInstanceDataFilePath).reader().readText() + ":5000"

        val imageUrl = "$dockerRegistryUrl/$nginxImageName"
        exec {
            commandLine("docker")
            args("tag", nginxImageName, imageUrl)
        }
        exec {
            commandLine("docker")
            args("push", imageUrl)
        }
    }
}

// endregion

// region Helpers

/* Arguments for SSH communication that ignore checking if the host is known */
val sshArgs = arrayOf("-o", "StrictHostKeyChecking=no", "-o", "UserKnownHostsFile=/dev/null")

// endregion
