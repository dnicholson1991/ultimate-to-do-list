package com.customsolutions.android.utl

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatCheckBox

/** A checkbox that includes a 3rd indeterminate state. Code is from:
 * https://github.com/imhardiklakhani/IndeterminateCheckbox */
class TriStateCheckbox : AppCompatCheckBox {
    private var state = 0

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(
            context,
            attrs
    ) {
        init()
    }

    constructor(
            context: Context,
            attrs: AttributeSet?,
            defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        state = UNCHECKED
        updateBtn()
        setOnCheckedChangeListener { buttonView, isChecked ->
            // The user of this class must update the state.
            /*
            state = when (state) {
                INDETERMINATE -> CHECKED
                UNCHECKED -> INDETERMINATE
                CHECKED -> UNCHECKED
                else -> CHECKED
            } */
            updateBtn()
        }
    }

    private fun updateBtn() {
        var btnDrawable = Util.resourceIdFromAttr(context,R.attr.checkbox_indeterminate)
        btnDrawable = when (state) {
            INDETERMINATE -> Util.resourceIdFromAttr(context,R.attr.checkbox_indeterminate)
            UNCHECKED -> Util.resourceIdFromAttr(context,R.attr.checkbox_unchecked)
            CHECKED -> Util.resourceIdFromAttr(context,R.attr.checkbox_checked)
            else -> Util.resourceIdFromAttr(context,R.attr.checkbox_unchecked)
        }
        setButtonDrawable(btnDrawable)
    }

    fun getState(): Int {
        return state
    }

    fun setState(state: Int) {
        this.state = state
        updateBtn()
    }

    companion object {
        const val UNCHECKED = 0
        const val INDETERMINATE = 1
        const val CHECKED = 2
    }
}