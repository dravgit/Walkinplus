package com.example.walkinplus.utils

import android.app.ProgressDialog
import android.util.Log
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.example.walkin.cyp.models.BaseResponseModel
import com.example.walkinplus.WalkInPlusErrorModel
import com.example.walkinplus.models.FaceResponseModel
import com.example.walkinplus.utils.Util
import com.google.gson.Gson
import org.json.JSONObject


class NetworkUtil {
    companion object {
        private val STATUS_CODE_SUCCESS = 200
        private val URL_DOMAIN = "http://206.189.87.217"
        val URL_CHECK = "$URL_DOMAIN/api/v1/check"

        var progressdialog: ProgressDialog? = null

        fun CheckFace(face: String, serial: String, listener: NetworkLisener<FaceResponseModel>, kClass: Class<FaceResponseModel>) {
            AndroidNetworking.post(URL_CHECK)
                .addBodyParameter("image", face)
                .addBodyParameter("edc_id", "ED0001")
                .setTag("checkface")
                .setPriority(Priority.HIGH)
                .build()
                .getAsJSONObject(getResponseListener(kClass, listener))
        }

        private fun showLoadingDialog() {
            progressdialog = ProgressDialog(Util.activityContext)
            progressdialog?.setMessage("Please Wait....")
            progressdialog?.show()
        }

        private fun hideLoadingDialog() {
            try {
                progressdialog?.let {
                    if (it.isShowing) {
                        it.dismiss()
                    }
                }
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }
        }

        private fun <T : BaseResponseModel> getResponseListener(kClass: Class<T>, listener: NetworkLisener<T>): JSONObjectRequestListener {
            return object : JSONObjectRequestListener {
                override fun onResponse(response: JSONObject?) {
                    hideLoadingDialog()
                    Log.e("response",response.toString())
                    response?.let {
                        val status = it.getInt("status")
                        if (STATUS_CODE_SUCCESS.equals(status)) {
                            if (kClass.isAssignableFrom(FaceResponseModel::class.java)) {
                                listener.onResponse(Gson().fromJson(it.toString(), kClass))
                            } else {
                                val jsonData = it.getJSONObject("data")
                                listener.onResponse(Gson().fromJson(jsonData.toString(), kClass))
                            }
                        } else {
                            val obj = JSONObject().put("error_code", status)
                                .put("msg", it.getString("message"))
                            val walkInErrorModel = Gson().fromJson(obj.toString(), WalkInPlusErrorModel::class.java)
                            listener.onError(walkInErrorModel)
                            showError(status)
                        }
                    }
                }

                override fun onError(anError: ANError?) {
                    hideLoadingDialog()
                    anError?.let {
                        it.printStackTrace()
                        showError(it.errorCode)
                        val obj = JSONObject().put("error_code", it.errorCode)
                            .put("msg", it.message)
                        val walkInErrorModel = Gson().fromJson(obj.toString(), WalkInPlusErrorModel::class.java)
                        listener.onError(walkInErrorModel)
                    }

                }
            }
        }

        fun showError(status: Int) {
//            if (STATUS_CODE_COMPANY_NOT_FOUND == status) {
//                Util.showToast(R.string.not_found_company)
//            } else if (STATUS_CODE_SEARIAL_NOT_FOUND == status) {
//                Util.showToast(R.string.not_found_serial)
//            } else if(STATUS_CODE_REQUIRE == status){
//                Util.showToast(R.string.not_require_data)
//            } else{
//                Util.showToast(R.string.something_error)
//            }
        }

        interface NetworkLisener<T> {
            fun onResponse(response: T)
            fun onError(errorModel: WalkInPlusErrorModel)
            fun onExpired()
        }
    }
}