package com.example.beamshare.cloud

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.S3ClientOptions

object CloudflareR2 {

    private const val ACCESS_KEY = "your_access_key"
    private const val SECRET_KEY = "your_secret_key"
    private const val ENDPOINT = "https://YOUR_ACCOUNT_ID.r2.cloudflarestorage.com"
    const val BUCKET = "beamshare-storage"

    val client: AmazonS3Client by lazy {
        val credentials = BasicAWSCredentials(ACCESS_KEY, SECRET_KEY)
        val s3 = AmazonS3Client(credentials, Region.getRegion(Regions.US_EAST_1))
        s3.setEndpoint(ENDPOINT)
        s3.setS3ClientOptions(
            S3ClientOptions.builder().setPathStyleAccess(true).build()
        )
        s3
    }
}