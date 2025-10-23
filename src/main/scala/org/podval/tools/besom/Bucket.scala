package org.podval.tools.besom

import besom.api.gcp.serviceaccount.Account
import besom.{Context, NonEmptyString, Output}
import besom.api.gcp.storage.inputs.BucketWebsiteArgs
import besom.api.gcp.storage.{BucketArgs, Bucket as BucketGCP}

// pulumi import "gcp:storage/bucket:Bucket" "<name>" "<name>"
final class Bucket(
  project: Project,
  name: NonEmptyString,
  location: String,
  storageClas: String = "STANDARD",
  forceDestroy: Boolean = false,
  notFoundPage: Option[String] = None,
  isPublic: Boolean = false,
  serviceAccount: Option[ServiceAccount] = None
)(using ctx: Context) extends WithResources:
  val bucket: Output[BucketGCP] = BucketGCP(name, BucketArgs(
    project = project.project.projectId,
    name = name, // TODO do I need this?
    storageClass = storageClas,
    location = location,
    publicAccessPrevention = "inherited",
    uniformBucketLevelAccess = true,
    forceDestroy = forceDestroy,
    website = notFoundPage.map(notFoundPage => BucketWebsiteArgs(
      notFoundPage = notFoundPage
    ))
  ))

  override def resources: Seq[Output[?]] =
    val forPublic: Option[BucketIam[Principal.AllUsers.type]] =
      Option.when(isPublic)(BucketIam(this, Output(Principal.AllUsers), "storage.objectViewer"))
    val forServiceAccount: Option[BucketIam[Account]] =
      serviceAccount.map(serviceAccount => BucketIam(this, serviceAccount.account, "storage.objectAdmin"))

    Seq(bucket) ++ WithResources(
      forPublic.toList ++
      forServiceAccount.toList
    )
