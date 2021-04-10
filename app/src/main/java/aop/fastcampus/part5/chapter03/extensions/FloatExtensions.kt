package aop.fastcampus.part5.chapter03.extensions

import android.content.res.Resources

internal fun Float.fromDpToPx(): Int {
    return (this * Resources.getSystem().displayMetrics.density).toInt()
}
