import org.apache.tools.ant.util.TeeOutputStream
import java.io.ByteArrayOutputStream


// region Paths, directories and file names
val serverUrl = gradle.extra.get("server.ip") ?: error("Missing remote server ip")
val componentsDirectoryPath = rootDir.path + File.separator + "components"
val openVpnDirectoryPath = componentsDirectoryPath + File.separator + "openvpn"
val openVpnSetupDirectoryName = "openvpn_setup"
val openVpnSetupFileDataPath = buildDir.path + File.separator + "openvpn_status"
val vpnServerUser = "ubuntu"
val observabilityDirectoryPath = componentsDirectoryPath + File.separator + "observability"
val observabilityConfigsPath = observabilityDirectoryPath + File.separator + "configs"
val observabilityDataPath = observabilityDirectoryPath + File.separator + "read_path" + File.separator + "data"
// endregion

// region VPN

val pushVpnSetup by tasks.registering {
    group = "vpn"
    description = "Set up OpenVPN server"

    doLast {
        // Push installation scripts onto the server
        exec {
            workingDir = File(openVpnDirectoryPath)
            commandLine("scp")
            args(
                *sshArgs,
                "-r", openVpnDirectoryPath + File.separator + openVpnSetupDirectoryName,
                "$vpnServerUser@$serverUrl:~"
            )
        }
    }
}

val setupVpnServer by tasks.registering {
    group = "vpn"
    description = "Set up OpenVPN server"
    dependsOn(pushVpnSetup)

    doLast {
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
                    "$vpnServerUser@$serverUrl",
                    "sudo sh ~/$openVpnSetupDirectoryName/$script"
                )
            }
        }

        // Start OpenVPN
        exec {
            commandLine("ssh")
            args(
                *sshArgs,
                "$vpnServerUser@$serverUrl",
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
                "$vpnServerUser@$serverUrl",
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
        // Execute script
        exec {
            commandLine("ssh")
            args(
                *sshArgs,
                "$vpnServerUser@$serverUrl",
                "sudo sh ~/$openVpnSetupDirectoryName/create_client.sh $clientName $serverUrl"
            )
        }

        // Download client credentials
        exec {
            commandLine("scp")
            args(
                *sshArgs,
                "$vpnServerUser@$serverUrl:/etc/openvpn/clients/$clientName.ovpn", openVpnClientCredentialsFilePath
            )
        }
    }
}

// endregion

val installDocker by tasks.registering {
    group = "instances setup"
    description = "Install docker on server"

    doLast {
        val host = "ubuntu@$serverUrl"
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

val runPortainer by tasks.registering {
    group = "instances setup"
    description = "Install portainer on server"

    doLast {
        val host = "ubuntu@$serverUrl"
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

    doLast {
        val host = "ubuntu@$serverUrl"
        exec {
            // Execute docker installation script on remote host
            commandLine("ssh")
            args(
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                host, "sudo sh -c \"echo '{\\\"insecure-registries\\\" : [\\\"http://127.0.0.1:5000\\\"]}' > /etc/docker/daemon.json\""
            )
        }
    }
}

val uploadObservabilityConfigs by tasks.registering {
    group = "observability operations"
    description = "Upload configs for observability containers"


    doLast {
        val host = "ubuntu@$serverUrl"
        exec {
            commandLine("scp")
            args(
                "-prv",
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                observabilityConfigsPath,
                "$host:/components/observability",
            )
        }
    }
}


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

        val host = "ubuntu@$serverUrl"
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

// region Helpers

/* Arguments for SSH communication that ignore checking if the host is known */
val sshArgs = arrayOf("-o", "StrictHostKeyChecking=no", "-o", "UserKnownHostsFile=/dev/null")

// endregion
