/**
 *  This file is part of the ldbc.
 *  For the full copyright and license information,
 *  please view the LICENSE file that was distributed with this source code.
 */

import sbt.*

import org.typelevel.sbt.gha.GenerativePlugin.autoImport.*

import JavaVersions.*
import ScalaVersions.*

object Workflows {

  val sbtScripted: Def.Initialize[WorkflowJob] = Def.setting(
    WorkflowJob(
      "sbtScripted",
      "sbt scripted",
      githubWorkflowJobSetup.value.toList ::: List(
        WorkflowStep.Run(
          List("sbt +publishLocal"),
          name = Some("sbt publishLocal")
        ),
        WorkflowStep.Run(
          List("sbt scripted"),
          name = Some("sbt scripted")
        )
      ),
      scalas = List(scala3),
      javas  = List(JavaSpec.temurin(java11), JavaSpec.temurin(java17), JavaSpec.temurin(java21))
    )
  )

  val generateSSLCerts: WorkflowStep.Run = WorkflowStep.Run(
    commands = List("./script/generate-ssl-certs.sh"),
    name     = Some("Generate SSL certificates")
  )

  val dockerRun: WorkflowStep.Run = WorkflowStep.Run(
    commands = List("docker compose up -d"),
    name     = Some("Start up MySQL on Docker")
  )

  val dockerStop: WorkflowStep.Run = WorkflowStep.Run(
    commands = List("docker compose down"),
    name     = Some("Stop MySQL on Docker")
  )
}
