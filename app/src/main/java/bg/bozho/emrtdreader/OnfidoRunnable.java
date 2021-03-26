package bg.bozho.emrtdreader;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.widget.TextView;
import com.onfido.Onfido;
import com.onfido.exceptions.OnfidoException;
import com.onfido.models.*;

import java.io.*;
import java.util.*;

public class OnfidoRunnable extends EmrtReaderRunnable {

	Onfido onfido;
	Bitmap passportImage;
	Bitmap documentImage;

	public OnfidoRunnable(Onfido onfido, Bitmap passport, Bitmap document, Context ctx, Activity activity) {
		super(ctx, activity);

		this.onfido = onfido;
		this.passportImage = passport;
		this.documentImage = document;
	}

	@Override
	public void run() {
		// Create Applicant
		Applicant applicant = null;
		try {
			applicant = onfido.applicant.create(Applicant.request().firstName("John").lastName("Doe"));
		} catch (OnfidoException e) {
			e.printStackTrace();
			showToast("Onfido: Failed to create applicant");
		}

		if (applicant == null) {
			return;
		}

		// Upload Document
		Document document = null;

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		documentImage.compress(Bitmap.CompressFormat.PNG, 90, baos);

		try {
			Document.Request documentRequest = Document.request()
					.applicantId(applicant.getId())
					.type("driving_licence");

			document = onfido.document.upload(new ByteArrayInputStream(baos.toByteArray()), "document.png", documentRequest);
		} catch (OnfidoException | IOException e) {
			e.printStackTrace();
			showToast("Onfido: Failed to upload document");
		}

		if (document == null) {
			return;
		}

		// Upload Live Photo
		LivePhoto livePhoto = null;

		if (passportImage.getWidth() > 1000 || passportImage.getHeight() > 1000) {
			float aspectRatio = passportImage.getWidth() /
					(float) passportImage.getHeight();
			int width = 1000;
			int height = Math.round(width / aspectRatio);

			passportImage = Bitmap.createScaledBitmap(passportImage, width, height, false);
		}

		baos = new ByteArrayOutputStream();
		passportImage.compress(Bitmap.CompressFormat.PNG, 90, baos);

		try {
			LivePhoto.Request livePhoteRequest = LivePhoto.request().applicantId(applicant.getId());

			livePhoto = onfido.livePhoto.upload(new ByteArrayInputStream(baos.toByteArray()), "livePhoto.png", livePhoteRequest);
		} catch (OnfidoException | IOException e) {
			e.printStackTrace();
			showToast("Onfido: Failed to upload live photo");
		}

		if (livePhoto == null) {
			return;
		}

		// Create Check
		Check check = null;

		try {
			List<String> requestedReports = new ArrayList<>();
			requestedReports.add("document");
			requestedReports.add("facial_similarity_photo");

			Check.Request checkRequest = Check.request().applicantId(applicant.getId())
					.reportNames(requestedReports);

			check = onfido.check.create(checkRequest);

		} catch (OnfidoException e) {
			e.printStackTrace();
			showToast("Onfido: Failed to create check");
		}

		if (check == null) {
			return;
		}

		// Retrieve reports
		List<Report> reports = new ArrayList<>();

		for (String reportId : check.getReportIds()) {
			Report tmpReport = null;

			while(tmpReport == null || tmpReport.getResult() == null || !tmpReport.getStatus().equals("complete")) {
				tmpReport = retrieveReport(reportId);
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			reports.add(tmpReport);

			if (tmpReport.getStatus().equals("complete") && tmpReport.getName().equals("facial_similarity_photo")) {

				Double faceComparison = null;
				try {
					Object o = tmpReport.getBreakdown().get("face_comparison");
					o = ((Map) o).get("breakdown");
					o = ((Map) o).get("face_match");
					o = ((Map) o).get("properties");
					faceComparison = (Double) ((Map) o).get("score");
				} catch (Exception e) {
					e.printStackTrace();
				}

				String  faceDetected = null;
				try {
					Object o = tmpReport.getBreakdown().get("image_integrity");
					o = ((Map)o).get("breakdown");
					o = ((Map)o).get("face_detected");
					faceDetected = (String)((Map)o).get("result");
				} catch (Exception e) {
					e.printStackTrace();
				}

				String  sourceIntegrity = null;
				try {
					Object o = tmpReport.getBreakdown().get("image_integrity");
					o = ((Map)o).get("breakdown");
					o = ((Map)o).get("source_integrity");
					sourceIntegrity = (String)((Map)o).get("result");
				} catch (Exception e) {
					e.printStackTrace();
				}

				Double visualAuthenticity = null;
				try {
					Object o = tmpReport.getBreakdown().get("visual_authenticity");
					o = ((Map) o).get("breakdown");
					o = ((Map) o).get("spoofing_detection");
					o = ((Map) o).get("properties");
					visualAuthenticity = (Double) ((Map) o).get("score");
				} catch (Exception e) {
					e.printStackTrace();
				}

				String tmp = "";

				if (faceComparison != null) {
					tmp += "Face comparison: " + faceComparison.toString() + "\r\n";
				}
				if (faceDetected != null) {
					tmp += "Face detected: " + faceDetected + "\r\n";
				}
				if (sourceIntegrity != null) {
					tmp += "Source integrity: " + sourceIntegrity + "\r\n";
				}
				if (visualAuthenticity != null) {
					tmp += "Visual Authenticity: " + visualAuthenticity.toString() + "\r\n";
				}

				final String result = tmp;

				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						TextView resultsLbl = (TextView) activity.findViewById(R.id.results_lbl);
						resultsLbl.setText("Results: \r\n" + result);
					}
				});

			}

			showToast("Got reports");
		}
	}

	private Report retrieveReport(String reportId) {
		Report report = null;

		try {
			report = onfido.report.find(reportId);
		} catch (OnfidoException e) {
			e.printStackTrace();
			showToast("Onfido: Failed to retrieve report");
		}

		return report;
	}

}
