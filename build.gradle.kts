import groovy.json.JsonSlurper
import org.apache.tools.ant.util.TeeOutputStream
import java.io.ByteArrayOutputStream

// region Paths, directories and file names

val infrastructureDirectoryPath = rootDir.path + File.separator + "terraform"
val infrastructureDataFilePath = buildDir.path + File.separator + "infrastructure_state.json"
val componentsDirectoryPath = rootDir.path + File.separator + "components"
val vpnServerDataFilePath = buildDir.path + File.separator + "vpn_server_data"
val openVpnDirectoryPath = componentsDirectoryPath + File.separator + "openvpn"
val openVpnSetupDirectoryName = "openvpn_setup"
val openVpnSetupFileDataPath = buildDir.path + File.separator + "openvpn_status"
val vpnServerUser = "ubuntu"

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

// region Helpers

/* Arguments for SSH communication that ignore checking if the host is known */
val sshArgs = arrayOf("-o", "StrictHostKeyChecking=no", "-o", "UserKnownHostsFile=/dev/null")

// endregion
