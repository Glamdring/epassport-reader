package bg.bozho.emrtdreader;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.*;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.onfido.Onfido;
import net.sf.scuba.smartcards.CardService;
import net.sf.scuba.smartcards.CardServiceException;
import net.sf.scuba.tlv.TLVOutputStream;

import org.jmrtd.BACKeySpec;
import org.jmrtd.ChipAuthenticationResult;
import org.jmrtd.DESedeSecureMessagingWrapper;
import org.jmrtd.PassportService;
import org.jmrtd.TerminalAuthenticationResult;
import org.jmrtd.Util;
import org.jmrtd.cert.CVCAuthorizationTemplate;
import org.jmrtd.cert.CVCPrincipal;
import org.jmrtd.cert.CardVerifiableCertificate;
import org.jmrtd.lds.*;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.*;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.interfaces.DHPublicKey;

public class MainActivity extends AppCompatActivity {

	private String documentNumber = "xxxxxx";
	private String dateOfBirth = "YYMMDD";
	private String dateOfExpiry = "YYMMDD";

	private final String ONFIDO_SDK_TOKEN = "xxxxxxxx";

	public static final int TAKE_PHOTO = 100;

	private Bitmap passportImage = null;

	private Onfido onfido;

	TextView scanPassportLbl;

	TextView compareLbl;

	EditText documentNumberInput;

	DatePicker dateOfBirthInput;

	DatePicker dateOfExpiryInput;

	ImageView selfieImage;

	Bitmap selfieBitmap;

	Uri selfieUri;

	static {
		Security.addProvider(new BouncyCastleProvider());
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy);

		selfieImage = findViewById(R.id.selfieImg);

		documentNumberInput = findViewById(R.id.passportnr);
		documentNumberInput.setText(documentNumber);

		dateOfBirthInput = findViewById(R.id.dateOfBirth);
		dateOfBirthInput.updateDate(1990, 1, 1);

		dateOfExpiryInput = findViewById(R.id.dateOfExpiration);
		dateOfExpiryInput.updateDate(2022, 1, 1);

		Button scanNfc = (Button) findViewById(R.id.scanPassport);
		scanNfc.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

				if (getIntent() == null || getIntent().getExtras() == null) {
					showToast("No Passport found");
					return;
				}
				Tag tag = getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
				if (tag == null) {
					showToast("No Passport found");
					return;
				}

				documentNumber = documentNumberInput.getText().toString();
				dateOfBirth = convertDatepickerToString(dateOfBirthInput);
				dateOfExpiry = convertDatepickerToString(dateOfExpiryInput);


				scanPassportLbl.setText("Scan Passport: scanning...");
				Timer t = new Timer();
				t.schedule(new TimerTask() {
					@Override
					public void run() {
						Looper.prepare();
						scanPassport();
					}
				}, 100);
			}
		});

		Button takeSelfie = findViewById(R.id.takeSelfie);
		takeSelfie.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				openCamera();
			}
		});


		onfido = Onfido.builder()
				.apiToken(ONFIDO_SDK_TOKEN)
				.build();

		Button compareImages = (Button) findViewById(R.id.compare);
		compareImages.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				compareLbl.setText("Compare Pictures: running...");
				Timer t = new Timer();
				t.schedule(new TimerTask() {
					@Override
					public void run() {
						Looper.prepare();
						compareImages();
					}
				}, 100);
			}
		});

		scanPassportLbl = findViewById(R.id.scanPassport_lbl);
		compareLbl = findViewById(R.id.compare_lbl);

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == TAKE_PHOTO && resultCode == RESULT_OK) {
			try {
				selfieBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selfieUri);
				selfieImage.setAdjustViewBounds(true);
				selfieImage.setMaxWidth(500);
				selfieImage.setMaxHeight(500);
				selfieImage.setImageBitmap(selfieBitmap);
			} catch (IOException e) {
				e.printStackTrace();
			}

//			Bundle extras = data.getExtras();
//			selfieBitmap = (Bitmap) extras.get("data");
//			selfieImage.setImageBitmap(selfieBitmap);
		}
	}



	private void scanPassport() {
		showToast("Scanning Passport");
		if (getIntent() == null || getIntent().getExtras() == null) {
			showToast("No Passport found");
			return;
		}
		Tag tag = getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
		if (tag == null) {
			showToast("No Passport found");
			return;
		}
		PassportService ps = null;
		try {
			IsoDep nfc = IsoDep.get(tag);
			CardService cs = CardService.getInstance(nfc);
			ps = new PassportService(cs);
			ps.open();

			ps.sendSelectApplet(false);
			BACKeySpec bacKey = new BACKeySpec() {
				@Override
				public String getDocumentNumber() {
					return documentNumber;
				}

				@Override
				public String getDateOfBirth() {
					return dateOfBirth;
				}

				@Override
				public String getDateOfExpiry() {
					return dateOfExpiry;
				}
			};

			ps.doBAC(bacKey);

			InputStream is = null;
			InputStream is14 = null;
			InputStream isCvca = null;
			try {
				// Basic data
				is = ps.getInputStream(PassportService.EF_DG1);
				final DG1File dg1 = (DG1File) LDSFileUtil.getLDSFile(PassportService.EF_DG1, is);

				is = ps.getInputStream(PassportService.EF_DG2);
				final DG2File dg5 = (DG2File) LDSFileUtil.getLDSFile(PassportService.EF_DG2, is);

				InputStream img = dg5.getFaceInfos().get(0).getFaceImageInfos().get(0).getImageInputStream();
				Bitmap bitmap = BitmapFactory.decodeStream(img);
				passportImage = bitmap;
				img.close();

				runOnUiThread(new Runnable() {
					@Override
					public void run() {

						if (passportImage != null) {
							ImageView imageView = (ImageView) findViewById(R.id.passportImg);
							imageView.setImageBitmap(passportImage);
						}

					}
				});


//				// Chip Authentication
//				is14 = ps.getInputStream(PassportService.EF_DG14);
//				DG14File dg14 = (DG14File) LDSFileUtil.getLDSFile(PassportService.EF_DG14, is14);
//				Map<BigInteger, PublicKey> keyInfo = dg14.getChipAuthenticationPublicKeyInfos();
//				Map.Entry<BigInteger, PublicKey> entry = keyInfo.entrySet().iterator().next();
//				Log.i("EMRTD", "Chip Authentication starting");
//				ChipAuthenticationResult caResult = doCA(ps, entry.getKey(), entry.getValue());
//				Log.i("EMRTD", "Chip authentnication succeeded");
//
//				// CVCA
//				isCvca = ps.getInputStream(PassportService.EF_CVCA);
//				CVCAFile cvca = (CVCAFile) LDSFileUtil.getLDSFile(PassportService.EF_CVCA, isCvca);

				//TODO EAC
			} catch (Exception e) {
				showToast("Lost connection to passport");
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						scanPassportLbl.setText("Scan Passport: error");
					}
				});
				e.printStackTrace();
			} finally {
				try {
					is.close();
					is14.close();
					isCvca.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					scanPassportLbl.setText("Scan Passport: finished");
				}
			});

		} catch (CardServiceException e) {
			showToast("Failed to open passport");
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					scanPassportLbl.setText("Scan Passport: error");
				}
			});
			e.printStackTrace();
		} finally {
			try {
				ps.close();
			} catch (Exception ex) {
				showToast("Failed to close passport");
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						scanPassportLbl.setText("Scan Passport: error");
					}
				});
				ex.printStackTrace();
			}
		}
	}

	/**
	 * Copy pasted, because original uses explicit cast to BouncyCastle key implementation, whereas we have a spongycastle one
	 */
	public synchronized ChipAuthenticationResult doCA(PassportService ps, BigInteger keyId, PublicKey publicKey) throws CardServiceException {
		if (publicKey == null) {
			throw new IllegalArgumentException("Public key is null");
		}
		try {
			String agreementAlg = Util.inferKeyAgreementAlgorithm(publicKey);
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(agreementAlg);
			AlgorithmParameterSpec params = null;
			if ("DH".equals(agreementAlg)) {
				DHPublicKey dhPublicKey = (DHPublicKey) publicKey;
				params = dhPublicKey.getParams();
			} else if ("ECDH".equals(agreementAlg)) {
				ECPublicKey ecPublicKey = (ECPublicKey) publicKey;
				params = ecPublicKey.getParams();
			} else {
				throw new IllegalStateException("Unsupported algorithm \"" + agreementAlg + "\"");
			}
			keyPairGenerator.initialize(params);
			KeyPair keyPair = keyPairGenerator.generateKeyPair();

			KeyAgreement agreement = KeyAgreement.getInstance(agreementAlg);
			agreement.init(keyPair.getPrivate());
			agreement.doPhase(publicKey, true);

			byte[] secret = agreement.generateSecret();

			// TODO: this SHA1ing may have to be removed?
			// TODO: this hashing is needed for our Java Card passport applet implementation
			// byte[] secret = md.digest(secret);

			byte[] keyData = null;
			byte[] idData = null;
			byte[] keyHash = new byte[0];
			if ("DH".equals(agreementAlg)) {
				DHPublicKey dhPublicKey = (DHPublicKey) keyPair.getPublic();
				keyData = dhPublicKey.getY().toByteArray();
				// TODO: this is probably wrong, what should be hashed?
				MessageDigest md = MessageDigest.getInstance("SHA1");
				md = MessageDigest.getInstance("SHA1");
				keyHash = md.digest(keyData);
			} else if ("ECDH".equals(agreementAlg)) {
				org.spongycastle.jce.interfaces.ECPublicKey ecPublicKey = (org.spongycastle.jce.interfaces.ECPublicKey) keyPair.getPublic();
				keyData = ecPublicKey.getQ().getEncoded();
				byte[] t = Util.i2os(ecPublicKey.getQ().getX().toBigInteger());
				keyHash = Util.alignKeyDataToSize(t, ecPublicKey.getParameters().getCurve().getFieldSize() / 8);
			}
			keyData = Util.wrapDO((byte) 0x91, keyData);
			if (keyId.compareTo(BigInteger.ZERO) >= 0) {
				byte[] keyIdBytes = keyId.toByteArray();
				idData = Util.wrapDO((byte) 0x84, keyIdBytes);
			}
			ps.sendMSEKAT(ps.getWrapper(), keyData, idData);

			SecretKey ksEnc = Util.deriveKey(secret, Util.ENC_MODE);
			SecretKey ksMac = Util.deriveKey(secret, Util.MAC_MODE);

			ps.setWrapper(new DESedeSecureMessagingWrapper(ksEnc, ksMac, 0L));
			Field fld = PassportService.class.getDeclaredField("state");
			fld.setAccessible(true);
			fld.set(ps, 4); //PassportService.CA_AUTHENTICATED_STATE)
			return new ChipAuthenticationResult(keyId, publicKey, keyHash, keyPair);
		} catch (Exception e) {
			e.printStackTrace();
			throw new CardServiceException(e.toString());
		}
	}


	public synchronized TerminalAuthenticationResult doTA(PassportService ps, CVCPrincipal caReference, List<CardVerifiableCertificate> terminalCertificates,
														  PrivateKey terminalKey, String taAlg, ChipAuthenticationResult chipAuthenticationResult, String documentNumber) throws CardServiceException {
		try {
			if (terminalCertificates == null || terminalCertificates.size() < 1) {
				throw new IllegalArgumentException("Need at least 1 certificate to perform TA, found: " + terminalCertificates);
			}

			byte[] caKeyHash = chipAuthenticationResult.getKeyHash();
			/* The key hash that resulted from CA. */
			if (caKeyHash == null) {
				throw new IllegalArgumentException("CA key hash is null");
			}

			/* FIXME: check that terminalCertificates holds a (inverted, i.e. issuer before subject) chain. */

			/* Check if first cert is/has the expected CVCA, and remove it from chain if it is the CVCA. */
			CardVerifiableCertificate firstCert = terminalCertificates.get(0);
			CVCAuthorizationTemplate.Role firstCertRole = firstCert.getAuthorizationTemplate().getRole();
			if (CVCAuthorizationTemplate.Role.CVCA.equals(firstCertRole)) {
				CVCPrincipal firstCertHolderReference = firstCert.getHolderReference();
				if (caReference != null && !caReference.equals(firstCertHolderReference)) {
					throw new CardServiceException("First certificate holds wrong authority, found " + firstCertHolderReference.getName() + ", expected " + caReference.getName());
				}
				if (caReference == null) {
					caReference = firstCertHolderReference;
				}
				terminalCertificates.remove(0);
			}
			CVCPrincipal firstCertAuthorityReference = firstCert.getAuthorityReference();
			if (caReference != null && !caReference.equals(firstCertAuthorityReference)) {
				throw new CardServiceException("First certificate not signed by expected CA, found " + firstCertAuthorityReference.getName() + ",  expected " + caReference.getName());
			}
			if (caReference == null) {
				caReference = firstCertAuthorityReference;
			}

			/* Check if the last cert is an IS cert. */
			CardVerifiableCertificate lastCert = terminalCertificates.get(terminalCertificates.size() - 1);
			CVCAuthorizationTemplate.Role lastCertRole = lastCert.getAuthorizationTemplate().getRole();
			if (!CVCAuthorizationTemplate.Role.IS.equals(lastCertRole)) {
				throw new CardServiceException("Last certificate in chain (" + lastCert.getHolderReference().getName() + ") does not have role IS, but has role " + lastCertRole);
			}
			CardVerifiableCertificate terminalCert = lastCert;

			int i = 0;
			/* Have the MRTD check our chain. */
			for (CardVerifiableCertificate cert : terminalCertificates) {
				try {
					CVCPrincipal authorityReference = cert.getAuthorityReference();

					/* Step 1: MSE:SetDST */
					/* Manage Security Environment: Set for verification: Digital Signature Template,
					 * indicate authority of cert to check.
					 */
					byte[] authorityRefBytes = Util.wrapDO((byte) 0x83, authorityReference.getName().getBytes("ISO-8859-1"));
					ps.sendMSESetDST(ps.getWrapper(), authorityRefBytes);

					/* Cert body is already in TLV format. */
					byte[] body = cert.getCertBodyData();

					/* Signature not yet in TLV format, prefix it with tag and length. */
					byte[] signature = cert.getSignature();
					ByteArrayOutputStream sigOut = new ByteArrayOutputStream();
					TLVOutputStream tlvSigOut = new TLVOutputStream(sigOut);
					tlvSigOut.writeTag(0x5F37); //TAG_CVCERTIFICATE_SIGNATURE);
					tlvSigOut.writeValue(signature);
					tlvSigOut.close();
					signature = sigOut.toByteArray();

					/* Step 2: PSO:Verify Certificate */
					ps.sendPSOChainMode(ps.getWrapper(), body, signature);
				} catch (Exception cse) {
					Log.w("FOO", String.valueOf(i));
					throw cse;
				}
				i++;
			}

			if (terminalKey == null) {
				throw new CardServiceException("No terminal key");
			}

			/* Step 3: MSE Set AT */
			CVCPrincipal holderRef = terminalCert.getHolderReference();
			byte[] holderRefBytes = Util.wrapDO((byte) 0x83, holderRef.getName().getBytes("ISO-8859-1"));
			/* Manage Security Environment: Set for external authentication: Authentication Template */
			ps.sendMSESetATExtAuth(ps.getWrapper(), holderRefBytes);

			/* Step 4: send get challenge */
			byte[] rPICC = ps.sendGetChallenge(ps.getWrapper());

			/* Step 5: external authenticate. */
			/* FIXME: idPICC should be public key in case of PACE. See BSI TR 03110 v2.03 4.4. */
			byte[] idPICC = new byte[documentNumber.length() + 1];
			System.arraycopy(documentNumber.getBytes("ISO-8859-1"), 0, idPICC, 0, documentNumber.length());
			idPICC[idPICC.length - 1] = (byte) MRZInfo.checkDigit(documentNumber);

			ByteArrayOutputStream dtbs = new ByteArrayOutputStream();
			dtbs.write(idPICC);
			dtbs.write(rPICC);
			dtbs.write(caKeyHash);
			dtbs.close();
			byte[] dtbsBytes = dtbs.toByteArray();

			String sigAlg = terminalCert.getSigAlgName();
			if (sigAlg == null) {
				throw new IllegalStateException("ERROR: Could not determine signature algorithm for terminal certificate " + terminalCert.getHolderReference().getName());
			}
			Signature sig = Signature.getInstance(sigAlg);
			sig.initSign(terminalKey);
			sig.update(dtbsBytes);
			byte[] signedData = sig.sign();
			if (sigAlg.toUpperCase().endsWith("ECDSA")) {
				int keySize = ((org.bouncycastle.jce.interfaces.ECPrivateKey) terminalKey).getParameters().getCurve().getFieldSize() / 8;
				signedData = Util.getRawECDSASignature(signedData, keySize);
			}
			ps.sendMutualAuthenticate(ps.getWrapper(), signedData);
			Field fld = PassportService.class.getDeclaredField("state");
			fld.setAccessible(true);
			fld.set(ps, 5); //PassportService.TA_AUTHENTICATED_STATE)
			return new TerminalAuthenticationResult(chipAuthenticationResult, caReference, terminalCertificates, terminalKey, documentNumber, rPICC);
		} catch (CardServiceException cse) {
			throw cse;
		} catch (Exception e) {
			throw new CardServiceException(e.toString());
		}
	}

	private void showToast(String message) {
		Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
		toast.show();
	}

	private void compareImages() {

		OnfidoRunnable or = new OnfidoRunnable(onfido, selfieBitmap, passportImage, getApplicationContext(), this);

		final Thread onfidoThread = new Thread(or);
		onfidoThread.start();

		Timer t = new Timer();
		t.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				if (!onfidoThread.isAlive()) {
					Looper.prepare();
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							compareLbl.setText("Compare Pictures: finished");
						}
					});
					this.cancel();
				}
			}
		}, 50, 50);

	}

	private String convertDatepickerToString(DatePicker dp) {
		StringBuilder result = new StringBuilder();

		if (dp != null) {

			result.append(dp.getYear() % 100);

			if (dp.getMonth() < 10) {
				result.append(0);
			}
			result.append(dp.getMonth());


			if (dp.getDayOfMonth() < 10) {
				result.append(0);
			}
			result.append(dp.getDayOfMonth());
		}

		return result.toString();
	}

	private void openCamera() {
		// check for camera permission
		int permissionCheck = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA);

		// do we have camera permission?
		if (permissionCheck == PackageManager.PERMISSION_GRANTED) {

			// we have camera permission, open System camera
//			Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
//			startActivityForResult(intent, TAKE_PHOTO);

			dispatchTakePictureIntent();

		}
		else {

			// we don't have it, request camera permission from system
			ActivityCompat.requestPermissions(this,
					new String[]{Manifest.permission.CAMERA},
					1000);
		}
	}

	private File createImageFile(String currentPhotoPath) throws IOException {
		// Create an image file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String imageFileName = "JPEG_" + timeStamp + "_";
		File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
		File image = File.createTempFile(
				imageFileName,  /* prefix */
				".jpg",         /* suffix */
				storageDir      /* directory */
		);

		// Save a file: path for use with ACTION_VIEW intents
		currentPhotoPath = image.getAbsolutePath();
		return image;
	}

	private void dispatchTakePictureIntent() {
		String currentPhotoPath = getApplicationInfo().dataDir;
		Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		// Ensure that there's a camera activity to handle the intent
		if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
			// Create the File where the photo should go
			File photoFile = null;
			try {
				photoFile = createImageFile(currentPhotoPath);
			} catch (IOException ex) {
				// Error occurred while creating the File
			}
			// Continue only if the File was successfully created
			if (photoFile != null) {
				selfieUri = FileProvider.getUriForFile(this,
						"bg.bozho.emrtdreader.fileprovider",
						photoFile);
				takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, selfieUri);
				startActivityForResult(takePictureIntent, TAKE_PHOTO);
			}
		}
	}

}
