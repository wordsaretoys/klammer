package com.wordsaretoys.klammer;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;

public class ErrorDialog extends DialogFragment {

	public static ErrorDialog newInstance(int msgId) {
		ErrorDialog dialog = new ErrorDialog();
		Bundle args = new Bundle();
		args.putInt("message", msgId);
		dialog.setArguments(args);
		return dialog;
	}
	
	@Override
	public Dialog onCreateDialog(Bundle state) {
		AlertDialog.Builder builder = 
				new AlertDialog.Builder(getActivity());
		builder.setMessage(getArguments().getInt("message"));
		builder.setPositiveButton(R.string.buttonClose, null);
		AlertDialog dialog = builder.create();
		dialog.setCanceledOnTouchOutside(false);
		return dialog;
	}

}
