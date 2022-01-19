package com.example.walkinplus

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast

fun Toast.showCustomToastPass(activity: Activity)
{
    val layout = activity.layoutInflater.inflate (
            R.layout.nfc_pass_toast,
            activity.findViewById(R.id.nfc_pass_toast)
    )

    // use the application extension function
    this.apply {
        setGravity(Gravity.BOTTOM, 0, 40)
        duration = Toast.LENGTH_LONG
        view = layout
        show()
    }
}

fun Toast.showCustomToastNotPass(activity: Activity)
{
    val layout = activity.layoutInflater.inflate (
            R.layout.nfc_not_pass_toast,
            activity.findViewById(R.id.nfc_not_pass_toast)
    )

    // use the application extension function
    this.apply {
        setGravity(Gravity.BOTTOM, 0, 40)
        duration = Toast.LENGTH_LONG
        view = layout
        show()
    }
}

fun Toast.showCustomToastFacePass(activity: Activity, temperature: String)
{
    val layout = activity.layoutInflater.inflate (
            R.layout.pass_toast,
            activity.findViewById(R.id.pass_toast)
    )

    val layout1 = activity.layoutInflater.inflate (
            R.layout.pass_toast,
            activity.findViewById(R.id.temperature_view)
    )

    layout1.findViewById<TextView>(R.id.temperature_view).text = temperature + " °C"

    // use the application extension function
    this.apply {
        setGravity(Gravity.BOTTOM, 0, 40)
        duration = Toast.LENGTH_LONG
        view = layout
        setGravity(Gravity.BOTTOM, 0, 40)
        duration = Toast.LENGTH_LONG
        view = layout1
        show()
    }
}

fun Toast.showCustomToastFaceNotPass(activity: Activity, temperature: String)
{
    val layout = activity.layoutInflater.inflate (
            R.layout.not_pass_toast,
            activity.findViewById(R.id.not_pass_toast)
    )

    val layout1 = activity.layoutInflater.inflate (
            R.layout.not_pass_toast,
            activity.findViewById(R.id.temperature_view)
    )

    layout1.findViewById<TextView>(R.id.temperature_view).text = temperature + " °C"

    // use the application extension function
    this.apply {
        setGravity(Gravity.BOTTOM, 0, 40)
        duration = Toast.LENGTH_LONG
        view = layout
        setGravity(Gravity.BOTTOM, 0, 40)
        duration = Toast.LENGTH_LONG
        view = layout1
        show()
    }
}

fun Toast.showCustomToastWarning(activity: Activity, notification: String)
{
    val layout = activity.layoutInflater.inflate (
            R.layout.warning_toast,
            activity.findViewById(R.id.warning_toast)
    )

    val layout1 = activity.layoutInflater.inflate (
            R.layout.warning_toast,
            activity.findViewById(R.id.warning)
    )

    layout1.findViewById<TextView>(R.id.warning).text = notification

    // use the application extension function
    this.apply {
        setGravity(Gravity.BOTTOM, 0, 40)
        duration = Toast.LENGTH_LONG
        view = layout
        setGravity(Gravity.BOTTOM, 0, 40)
        duration = Toast.LENGTH_LONG
        view = layout1
        show()
    }
}

