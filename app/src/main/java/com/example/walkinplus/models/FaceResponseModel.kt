package com.example.walkinplus.models

import com.example.walkin.cyp.models.BaseResponseModel

open class FaceResponseModel (val status_code: String, val message: String) : BaseResponseModel()