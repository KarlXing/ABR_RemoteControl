package abr.teleop;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import android.view.View;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.Utils;

public class Controller extends Activity{
	private final static String TAG = "CameraRobot-Controller";
	
    public static final int MESSAGE_DATA_RECEIVE = 0;
    
    Button buttonUp, buttonDown, buttonLeft, buttonRight;
    ImageView imageView1;
    CheckBox cbFlash;
	CheckBox cbLog;
    
    RelativeLayout layout_joystick, layout_joystick_PT;
	JoyStickClass js, js_PT;
    int screenWidth, screenHeight;
    
	Boolean task_state = true;
	
	OutputStream out; 
	DataOutputStream dos;
	InputStream in;
	DataInputStream dis;
	
	Socket s;
	String ip, pass;

	int pwm_speed = 1500;
	int pwm_steering = 1500;

	private Scalar mLowerBound1 = new Scalar(0,0,80);  //road 1
	private Scalar mUpperBound1 = new Scalar(50,50,254);

	private Scalar mLowerBound2 = new Scalar(90,30,5);   //road shadow
	private Scalar mUpperBound2 = new Scalar(120,255,120);

	private Scalar mLowerBound3 = new Scalar(80,0,100);  //road2
	private Scalar mUpperBound3 = new Scalar(175,60,170);

	Mat received_image = new Mat();
	Mat backup_image = new Mat();
	Mat debug_image = new Mat();
	Mat shown_image = new Mat();
	Mat mask1 = new Mat();
	Mat mask2 = new Mat();
	Mat mask3 = new Mat();
	Mat roadImage = new Mat();
	Mat mHierarchy = new Mat();
	Mat image = new Mat();
	Mat road1mask = new Mat();
	Mat road2mask = new Mat();
	Mat roadshadowmask = new Mat();
	Mat roadmask = new Mat();

	int dilateSize = 1;
	int thresh = 0;
	int maxVal = 255;
	boolean debug = false;
	int state = 0;
	int state2 = 0;

	Mat dilation_kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,
			new Size(2*dilateSize + 1, 2*dilateSize+1),
			new org.opencv.core.Point(dilateSize, dilateSize));

	static {
		if (!OpenCVLoader.initDebug()) {
			Log.e("CameraView","OpenCV fail to init");
		}
	}

	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN 
        		| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE); 
		setContentView(R.layout.controller);
 
        Display display = getWindowManager().getDefaultDisplay(); 

        screenWidth = display.getWidth();
        screenHeight = display.getHeight();
        
		ip = getIntent().getExtras().getString("IP");
		pass = getIntent().getExtras().getString("Pass");
		
		imageView1 = (ImageView)findViewById(R.id.imageView1);


		layout_joystick = (RelativeLayout)findViewById(R.id.layout_joystick);
	    js = new JoyStickClass(getApplicationContext()
        		, layout_joystick, R.drawable.image_button);
	    js.setStickSize(screenHeight / 7, screenHeight / 7);
	    js.setLayoutSize(screenHeight / 2, screenHeight / 2);
	    js.setLayoutAlpha(50);
	    js.setStickAlpha(255);
	    js.setOffset((int)((screenHeight / 9) * 0.6));
	    js.setMinimumDistance((int)((screenHeight / 9) * 0.6));
	    
	    layout_joystick.setOnTouchListener(new OnTouchListener() {
	    	long time = System.currentTimeMillis();
			public boolean onTouch(View arg0, MotionEvent arg1) {
				js.drawStick(arg1);
				if(arg1.getAction() == MotionEvent.ACTION_DOWN) {
					command();
				} else if(arg1.getAction() == MotionEvent.ACTION_MOVE) {
					if(System.currentTimeMillis() - time > 200) {
						command();
						time = System.currentTimeMillis();
					}
				} else if(arg1.getAction() == MotionEvent.ACTION_UP) {
					//send("SS");
					//send("SS");
					pwm_speed = 1500;
					send("MC/" + pwm_speed + "/" + pwm_steering);
				}
				return true;
			}

			public void command()
			{
				pwm_speed = 1500 - (int)(250*js.getnormY());		//reverse Y

				/*
				if(pwm_speed < 1500)
					pwm_steering = 1500 + (int)(500*js.getnormX());    //reverse X
				else
					pwm_steering = 1500 - (int)(500*js.getnormX());    //reverse X
				*/
				send("MC/" + pwm_speed + "/" + pwm_steering);
			}
        });

	    
	    //***********************************  added joystick for pan and tilt unit control   *************************************/
	    layout_joystick_PT = (RelativeLayout)findViewById(R.id.layout_joystick2);
	    js_PT = new JoyStickClass(getApplicationContext()
        		, layout_joystick_PT, R.drawable.image_button);
	    js_PT.setStickSize(screenHeight / 7, screenHeight / 7);
	    js_PT.setLayoutSize(screenHeight / 2, screenHeight / 2);
	    js_PT.setLayoutAlpha(50);
	    js_PT.setStickAlpha(255);
	    js_PT.setOffset((int)((screenHeight / 9) * 0.6));
//	    js_PT.setMinimumDistance((int)((screenHeight / 9) * 0.6));
	    
	    layout_joystick_PT.setOnTouchListener(new OnTouchListener() {
	    	long time = System.currentTimeMillis();
			public boolean onTouch(View arg0, MotionEvent arg1) {
				js_PT.drawStick(arg1);
				if(arg1.getAction() == MotionEvent.ACTION_DOWN) {
					command();
				} else if(arg1.getAction() == MotionEvent.ACTION_MOVE) {
//					if(System.currentTimeMillis() - time > 200) {
						command();
//						time = System.currentTimeMillis(); 
//					}
				} else if(arg1.getAction() == MotionEvent.ACTION_UP) {
					//send("PT_SS");
					//send("PT_SS");
					pwm_steering = 1500;
					send("MC/" + pwm_speed + "/" + pwm_steering);
				}
				return true;
			}
			
			public void command() 
			{				
				//int pwm_pan = 1500 + (int)(500*js_PT.getnormX());
				//int pwm_tilt = 1500 + (int)(500*js_PT.getnormY());

				pwm_steering = 1500 + (int)(200*js_PT.getnormX());

//				Log.e("controller", "pwm_pan: " + pwm_pan + " pwm_tilt: " + pwm_tilt);
				
				//send("PT/" + pwm_pan + "/" + pwm_tilt);
				send("MC/" + pwm_speed + "/" + pwm_steering);
			}
        });
	    
	    //******************************************************************************************************************/
	    
	    
	    
	    Button buttonSnap = (Button)findViewById(R.id.btnPhotoSnap);
	    buttonSnap.setOnClickListener(new OnClickListener() {
	    	public void onClick(View v) {
				sendString("Snap");
	    	}
	    });
	    
	    Button buttonFocus = (Button)findViewById(R.id.btnAutoFocus);
	    buttonFocus.setOnClickListener(new OnClickListener() {
	    	public void onClick(View v) {
				sendString("Focus");
	    	}
	    });

	    Button buttonRun = (Button) findViewById(R.id.button_run);
	    buttonRun.setOnClickListener(new OnClickListener() {
	    	public void onClick(View v) {
				pwm_speed = 1700;
				pwm_steering = 1500;
				send("MC/" + pwm_speed + "/" + pwm_steering);
	    	}
	    });

	    Button buttonStop = (Button) findViewById(R.id.button_stop);
		buttonStop.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				pwm_speed = 1500;
				pwm_steering = 1500;
				send("MC/" + pwm_speed + "/" + pwm_steering);
			}
		});

		Button buttonDebug = (Button) findViewById(R.id.debug);
		buttonDebug.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (debug == false) {
					debug = true;
				} else {
					debug = false;
				}
			}
		});
//
//		Button buttonCheck= (Button) findViewById(R.id.check);
//		buttonCheck.setOnClickListener(new OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				EditText editText = (EditText) findViewById(R.id.position);
//				String[] pos = editText.getText().toString().split(",");
//				int x = Integer.parseInt(pos[0]);
//				int y = Integer.parseInt(pos[1]);
//				double[] hsv;
//				if (state == 0){
//					hsv = backup_image.get(x, y);
//				} else {
//					hsv = debug_image.get(x, y);
//				}
//				TextView textView = (TextView) findViewById(R.id.colors);
//				if (hsv.length == 1){
//					textView.setText(""+hsv[0]);
//				} else {
//					textView.setText(""+hsv[0]+","+hsv[1]+","+hsv[2]);
//				}
//			}
//		});



//		Button buttonDstep = (Button) findViewById(R.id.dstep);
//		buttonDstep.setOnClickListener(new OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				if (debug == false){
//					return;
//				}
//				switch(state) {
//					case 0:  // gaussian blur
//						Log.i("step1","gaussian blur");
//						Log.i("image size",backup_image.size().toString()+","+backup_image.channels());
//						debug_image = backup_image;
//						Imgproc.GaussianBlur(debug_image, debug_image, new Size(5.0, 5.0), 0,0);
//						Imgproc.cvtColor(debug_image, shown_image, Imgproc.COLOR_BGRA2RGBA, 4);
//						setImage(shown_image);
//						state = 1;
//						break;
//					case 1:  // convert 2 hsv
//						Log.i("step2", "bgr2hsv");
//						Log.i("image size",debug_image.size().toString()+","+debug_image.channels());
//						Imgproc.cvtColor(debug_image, debug_image, Imgproc.COLOR_BGR2HSV);
//						Imgproc.cvtColor(debug_image, shown_image, Imgproc.COLOR_HSV2RGB);
//						Imgproc.cvtColor(shown_image, shown_image, Imgproc.COLOR_RGB2RGBA);
//						setImage(shown_image);
//						state = 2;
//						break;
//					case 2:
//						Log.i("step3", "mask1");
//						Log.i("image size",debug_image.size().toString()+","+debug_image.channels());
//						Core.inRange(debug_image, mLowerBound1, mUpperBound1, mask1);
//						Imgproc.cvtColor(mask1, shown_image, Imgproc.COLOR_GRAY2RGBA);
//						setImage(shown_image);
//						state = 3;
//						break;
//					case 3:
//						Log.i("step4", "mask2");
//						Log.i("image size",debug_image.size().toString()+","+debug_image.channels());
//						Core.inRange(debug_image, mLowerBound2, mUpperBound2, mask2);
//						Imgproc.cvtColor(mask2, shown_image, Imgproc.COLOR_GRAY2RGBA);
//						setImage(shown_image);
//						state = 4;
//						break;
//					case 4:
//						Log.i("step5", "mask3");
//						Log.i("image size",debug_image.size().toString()+","+debug_image.channels());
//						Core.inRange(debug_image, mLowerBound3, mUpperBound3, mask3);
//						Imgproc.cvtColor(mask3, shown_image, Imgproc.COLOR_GRAY2RGBA);
//						setImage(shown_image);
//						state = 5;
//						break;
//					case 5:
//						Log.i("step6", "add masks");
//						Log.i("image size",debug_image.size().toString()+","+debug_image.channels());
//						Core.add(mask1, mask2, mask1);
//						Core.add(mask1, mask3, mask1);
//						Core.bitwise_and(debug_image, debug_image, roadImage, mask1);
//						debug_image = roadImage;
//						Imgproc.cvtColor(debug_image, shown_image, Imgproc.COLOR_HSV2RGB);
//						Imgproc.cvtColor(shown_image, shown_image, Imgproc.COLOR_RGB2RGBA);
//						setImage(shown_image);
//						state = 6;
//						break;
//					case 6: // hsv2gray
//						Log.i("step7", "hsv2gray");
//						Log.i("image size",debug_image.size().toString()+","+debug_image.channels());
//						Imgproc.cvtColor(debug_image, debug_image, Imgproc.COLOR_HSV2RGB);
//						Imgproc.cvtColor(debug_image, debug_image, Imgproc.COLOR_RGB2GRAY);
//						Imgproc.cvtColor(debug_image, shown_image, Imgproc.COLOR_GRAY2RGBA);
//						setImage(shown_image);
//						state = 7;
//						break;
//					case 7:
//						Log.i("step8","dilate");
//						Log.i("image size",debug_image.size().toString()+","+debug_image.channels());
//						Imgproc.dilate(debug_image, debug_image, dilation_kernel);
//						Imgproc.dilate(debug_image, debug_image, dilation_kernel);
//						Imgproc.dilate(debug_image, debug_image, dilation_kernel);
//						Imgproc.cvtColor(debug_image, shown_image, Imgproc.COLOR_GRAY2RGBA);
//						setImage(shown_image);
//						state = 8;
//						break;
//					case 8:
//						Log.i("step9","thresh and maxcontour");
//						Log.i("image size",debug_image.size().toString()+","+debug_image.channels());
//						Imgproc.threshold(debug_image, debug_image, thresh, maxVal, Imgproc.THRESH_BINARY);
//						List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
//						Imgproc.findContours(debug_image, contours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);
//						double maxVal = 0;
//						int maxValIdx = 0;
//						Mat image = new Mat(debug_image.size(), debug_image.type(), Scalar.all(0.0));
//						if (contours.size() == 0){
//							debug_image = image;
//							break;
//						}
//						for (int contourIdx = 0; contourIdx < contours.size(); contourIdx++)
//						{
//							double contourArea = Imgproc.contourArea(contours.get(contourIdx));
//							if (maxVal < contourArea)
//							{
//								maxVal = contourArea;
//								maxValIdx = contourIdx;
//							}
//						}
//						Imgproc.drawContours(image, contours, maxValIdx, new Scalar(255), -1);
//						debug_image = image;
//						Imgproc.cvtColor(debug_image, shown_image, Imgproc.COLOR_GRAY2RGBA);
//						setImage(shown_image);
//						state = 9;
//						break;
//				}
//			}
//		});
//


        cbFlash = (CheckBox)findViewById(R.id.cbFlash);
        cbFlash.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
				if(arg1) {
					sendString("LEDON");
				} else {
					sendString("LEDOFF");
				}
			}
        });

		cbLog = (CheckBox)findViewById(R.id.cbLog);
		cbLog.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
				if(arg1) {
					sendString("LOGON");
					Toast.makeText(getApplicationContext()
							, "Logging on"
							, Toast.LENGTH_SHORT).show();
					cbLog.setBackgroundResource(R.drawable.log_pressed);
					pwm_speed = 1700;
					send("MC/" + pwm_speed + "/" + pwm_steering);
				} else {
					sendString("LOGOFF");
					Toast.makeText(getApplicationContext()
							, "Logging off"
							, Toast.LENGTH_SHORT).show();
					cbLog.setBackgroundResource(R.drawable.log_normal);
					pwm_speed = 1500;
					send("MC/" + pwm_speed + "/" + pwm_steering);
				}
			}
		});
		
		Runnable readThread = new Runnable() {
			Bitmap bitmap;
			
			public void run() {
				try {
					s = new Socket();
					s.connect((new InetSocketAddress(InetAddress.getByName(ip), 21111)), 5000);

					out = s.getOutputStream(); 
					dos = new DataOutputStream(out);

					in = s.getInputStream();
				    dis = new DataInputStream(in);
					sendString(pass);
					
					while(task_state) {
						try {
							int size = dis.readInt();
							final byte[] buff = new byte[size];
							dis.readFully(buff);
							runOnUiThread(new Runnable() {
					    		public void run() {
									if(buff.length > 0 && buff.length < 20) {
										if(new String(buff).equals("Snap")) {
											Toast.makeText(getApplicationContext()
													, "Take a photo"
													, Toast.LENGTH_SHORT).show();		
										} else if(new String(buff).equals("WRONG")) {
											Toast.makeText(getApplicationContext()
							    					, "Wrong Password", Toast.LENGTH_SHORT).show();
											finish();
										} else if(new String(buff).equals("ACCEPT")) {
									    	Toast.makeText(getApplicationContext()
									    			, "Connection accepted"
									    			, Toast.LENGTH_SHORT).show();	
										} else if(new String(buff).equals("NoFlash")) {
											Toast.makeText(getApplicationContext()
									    			, "Device not support flash"
									    			, Toast.LENGTH_SHORT).show();
										}								    		
						    		} else if(buff.length > 20) {
										if (debug == true) {
											bitmap = BitmapFactory.decodeByteArray(buff , 0, buff.length);
											Utils.bitmapToMat(bitmap, received_image);
											received_image = imgProcess(received_image);
											Imgproc.cvtColor(received_image, received_image, Imgproc.COLOR_GRAY2RGBA, 4);
											bitmap = Bitmap.createBitmap(received_image.cols(), received_image.rows(), Bitmap.Config.ARGB_8888);
											Utils.matToBitmap(received_image, bitmap);
											imageView1.setImageBitmap(bitmap);
										} else {
											bitmap = BitmapFactory.decodeByteArray(buff , 0, buff.length);
											imageView1.setImageBitmap(bitmap);
										}

						    		}
					    		}
							});
						} catch (EOFException e) {
							runOnUiThread(new Runnable() {
								public void run() {
									Toast.makeText(getApplicationContext()
											, "Connection Down"
											, Toast.LENGTH_SHORT).show();
								}
							});
							task_state = false;
							finish();
							Log.e(TAG, e.toString());
						} catch (NumberFormatException e) {
							Log.e(TAG, e.toString());
						} catch (UnknownHostException e) {
							Log.e(TAG, e.toString());
						} catch (IOException e) {
							Log.e(TAG, e.toString());
						}
					}
				} catch (NumberFormatException e) {
					Log.e(TAG, e.toString());
				} catch (UnknownHostException e) {
					Log.e(TAG, e.toString());
				} catch (IOException e) {
					Log.e(TAG, e.toString());
					runOnUiThread(new Runnable() {
						public void run() {
							Toast.makeText(getApplicationContext()
									, "Connection Failed"
									, Toast.LENGTH_SHORT).show();
						}
					});
					finish();
				}
			}
		};
		new Thread(readThread).start();

	}

	public void onPause() {
		super.onPause();

		task_state = false;
		try {
			s.close();
			out.close();
			dos.close();
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		} catch (NullPointerException e) {
			Log.e(TAG, e.toString());
		}
		
		finish();
	}
	
	public void sendString(String str) {
		try {
			dos.writeInt(str.length());
			dos.write(str.getBytes());
			out.flush();
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		} catch (NullPointerException e) {
			Log.e(TAG, e.toString());
		}
	}
	
	public void send(final String str) {
		new Thread(new Runnable() {
			public void run() {
				try {
//					Log.e("controller", "sending..." + str);
					
					DatagramSocket s = new DatagramSocket();
					InetAddress local = InetAddress.getByName(ip);
					DatagramPacket p = new DatagramPacket(str.getBytes(), str.getBytes().length, local, 21111);
					s.send(p);
					s.close();
				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (SocketException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();
		
	}

	//road center (RGB image)
	public Mat imgProcess(Mat image){
		// Gaussian blur
		Imgproc.GaussianBlur(image, image, new Size(5.0, 5.0), 0,0);
		// RGB2HSV
		Imgproc.cvtColor(image, image, Imgproc.COLOR_RGB2HSV);
		// Inrange selection
		Core.inRange(image, mLowerBound1, mUpperBound1, mask1);
		Core.inRange(image, mLowerBound2, mUpperBound2, mask2);
		Core.inRange(image, mLowerBound3, mUpperBound3, mask3);
		Core.add(mask1, mask2, mask1);
		Core.add(mask1, mask3, mask1);
		Core.bitwise_and(image, image, roadImage, mask1);  //cannot use image, image, image
		image = roadImage;
		// HSV2GRAY
		Imgproc.cvtColor(image, image, Imgproc.COLOR_HSV2BGR);
		Imgproc.cvtColor(image, image, Imgproc.COLOR_BGR2GRAY);
		// Dilate
		for (int i = 0; i < 2; i = i+1){
			Imgproc.dilate(image, image, dilation_kernel);
		}
		// Erode
		for (int i = 0; i < 2; i = i+1){
			Imgproc.erode(image, image, dilation_kernel);
		}
		// Gray thresh
		Imgproc.threshold(image, image, thresh, maxVal,Imgproc.THRESH_BINARY);
		// find contours
		List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		Imgproc.findContours(image, contours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);
		double maxVal = -1;
		int maxValIdx = -1;
		for (int contourIdx = 0; contourIdx < contours.size(); contourIdx++)
		{
			double contourArea = Imgproc.contourArea(contours.get(contourIdx));
			if (maxVal < contourArea)
			{
				maxVal = contourArea;
				maxValIdx = contourIdx;
			}
		}
		// fill maximum contour
		image = new Mat(image.size(), image.type(), Scalar.all(0.0));
		if (maxValIdx >= 0){
			Imgproc.drawContours(image, contours, maxValIdx, new Scalar(255), -1);
		}
		return image;
	}

	public void setImage(Mat image){
		Bitmap bitmap = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.ARGB_8888);
		Utils.matToBitmap(image, bitmap);
		imageView1.setImageBitmap(bitmap);
	}
}
