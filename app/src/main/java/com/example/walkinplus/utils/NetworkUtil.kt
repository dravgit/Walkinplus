package com.example.walkinplus.utils

import android.app.ProgressDialog
import android.content.Context
import android.os.Build
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
        private val STATUS_CODE_SUCCESS = 1201
        private val STATUS_CODE_ACCESS = 1202
        private val STATUS_CODE_EDC = 1203
        private val STATUS_CODE_REGISTER = 1204
        private val STATUS_CODE_VALIDATE = 1422
        private val URL_DOMAIN = "https://plus.walkinvms.com"
        val URL_CHECKFACE = "$URL_DOMAIN/api/v1/check"
        val URL_CHECKNFC = "$URL_DOMAIN/api/v1/check_nfc"

        var progressdialog: ProgressDialog? = null

        fun CheckFace(face: String,ctx: Context,  temperature: String, listener: NetworkLisener<FaceResponseModel>, kClass: Class<FaceResponseModel>) {
            Log.e("SN",Build.SERIAL)
            showLoadingDialog(ctx)
            AndroidNetworking.post(URL_CHECKFACE)
                    .addBodyParameter("image", face)
                    .addBodyParameter("edc_id", Build.SERIAL)
                    .addBodyParameter("temperature", temperature)
                    .setTag("checkface")
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsJSONObject(getResponseListener(kClass, listener))
        }

        fun CheckNfc(code: String,ctx: Context, listener: NetworkLisener<FaceResponseModel>, kClass: Class<FaceResponseModel>) {
            Log.e("SN",Build.SERIAL)
//            showLoadingDialog(ctx)
            AndroidNetworking.post(URL_CHECKNFC)
                    .addBodyParameter("nfc", code)
                    .addBodyParameter("edc_id", Build.SERIAL)
                    .setTag("checknfc")
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsJSONObject(getResponseListener(kClass, listener))
        }

        private fun showLoadingDialog(ctx: Context) {
            progressdialog = ProgressDialog(ctx)
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
                        } else if (STATUS_CODE_ACCESS.equals(status) || STATUS_CODE_EDC.equals(status) || STATUS_CODE_REGISTER.equals(status) || STATUS_CODE_VALIDATE.equals(status)) {
                            val obj = JSONObject().put("error_code", status)
                                .put("msg", it.getString("message"))
                            val walkInErrorModel = Gson().fromJson(obj.toString(), WalkInPlusErrorModel::class.java)
                            listener.onError(walkInErrorModel)
                            showError(status)
                        } else {
                            val obj = JSONObject().put("error_code", status)
                                    .put("msg", "Please contract Admin.")
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