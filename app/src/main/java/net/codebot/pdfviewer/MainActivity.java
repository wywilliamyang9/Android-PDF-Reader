package net.codebot.pdfviewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

// PDF sample code from
// https://medium.com/@chahat.jain0/rendering-a-pdf-document-in-android-activity-fragment-using-pdfrenderer-442462cb8f9a
// Issues about cache etc. are not at all obvious from documentation, so we should expect people to need this.
// We may wish to provide this code.

public class MainActivity extends AppCompatActivity {

    final String LOGNAME = "pdf_viewer";
    final String FILENAME = "shannon1948.pdf";
    final int FILERESID = R.raw.shannon1948;
//    final String FILENAME = "introduction.pdf";
//    final int FILERESID = R.raw.introduction;

    // manage the pages of the PDF, see below
    PdfRenderer pdfRenderer;
    private ParcelFileDescriptor parcelFileDescriptor;
    private PdfRenderer.Page currentPage;

    // custom ImageView class that captures strokes and draws them over the image
    PDFimage pageImage;

    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LinearLayout layout = findViewById(R.id.pdfLayout);
        pageImage = new PDFimage(this);
        layout.addView(pageImage, 1);
        layout.setEnabled(true);
        pageImage.setMinimumWidth(1000);
        pageImage.setMinimumHeight(2000);

        // set filename
        TextView filename = findViewById(R.id.name);
        filename.setText(FILENAME);

        // user interactions
        final ImageButton previous = findViewById(R.id.previousPage);
        final ImageButton next = findViewById(R.id.nextPage);
        final ImageButton undo = findViewById(R.id.undo);
        final ImageButton redo = findViewById(R.id.redo);
        final ImageButton pencil = findViewById(R.id.pencil);
        final ImageButton highlighter = findViewById(R.id.highlighter);
        final ImageButton eraser = findViewById(R.id.eraser);

        previous.setOnTouchListener(new View.OnTouchListener() { // handle previous page
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        Log.d("previous", "Action down");
                        previous.setBackgroundResource(R.drawable.clickedprevious);
                        break;
                    case MotionEvent.ACTION_UP:
                        Log.d("previous", "Action up");
                        previous.setBackgroundResource(R.drawable.previous);
//                        System.out.println("here");
                        // show previous page
                        showPage(pageImage.getPageNum() - 1);
//                        System.out.println("previous");
                        break;
                }
                return true;
            }
        });

        next.setOnTouchListener(new View.OnTouchListener() { // handle next page
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        Log.d("next", "Action down");
                        next.setBackgroundResource(R.drawable.clickednext);
                        break;
                    case MotionEvent.ACTION_UP:
                        Log.d("next", "Action up");
                        next.setBackgroundResource(R.drawable.next);
//                        System.out.println("here");
                        // show next page
                        showPage(pageImage.getPageNum() + 1);
//                        System.out.println("next");
                        break;
                }
                return true;
            }
        });

        undo.setOnTouchListener(new View.OnTouchListener() { // handle undo
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        Log.d("undo", "Action down");
                        undo.setBackgroundResource(R.drawable.clickedundo);
                        break;
                    case MotionEvent.ACTION_UP:
                        Log.d("undo", "Action up");
                        undo.setBackgroundResource(R.drawable.undo);
                        pageImage.undo();
                        break;
                }
                return true;
            }
        });

        redo.setOnTouchListener(new View.OnTouchListener() { // handle redo
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        Log.d("redo", "Action down");
                        redo.setBackgroundResource(R.drawable.clickedredo);
                        break;
                    case MotionEvent.ACTION_UP:
                        Log.d("redo", "Action up");
                        redo.setBackgroundResource(R.drawable.redo);
                        pageImage.redo();
                        break;
                }
                return true;
            }
        });

        pencil.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("pencil", "clicked");
                pageImage.setTool(ToolEnum.pencil);
                pencil.setBackgroundResource(R.drawable.clickedpencil);
                highlighter.setBackgroundResource(R.drawable.highlighter);
                eraser.setBackgroundResource(R.drawable.eraser);
            }
        });

        highlighter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("highlighter", "clicked");
                pageImage.setTool(ToolEnum.highlighter);
                pencil.setBackgroundResource(R.drawable.pencil);
                highlighter.setBackgroundResource(R.drawable.clickedhighlighter);
                eraser.setBackgroundResource(R.drawable.eraser);
            }
        });

        eraser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("eraser", "clicked");
                pageImage.setTool(ToolEnum.eraser);
                pencil.setBackgroundResource(R.drawable.pencil);
                highlighter.setBackgroundResource(R.drawable.highlighter);
                eraser.setBackgroundResource(R.drawable.clickederaser);
            }
        });

        // open page 0 of the PDF
        // it will be displayed as an image in the pageImage (above)
        try {
            openRenderer(this);
            showPage(0);
//            closeRenderer(); // need to keep pdfRenderer open
        } catch (IOException exception) {
            Log.d(LOGNAME, "Error opening PDF");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            closeRenderer();
        } catch (IOException ex) {
            Log.d(LOGNAME, "Unable to close PDF renderer");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void openRenderer(Context context) throws IOException {
        // In this sample, we read a PDF from the assets directory.
        File file = new File(context.getCacheDir(), FILENAME);
        if (!file.exists()) {
            // pdfRenderer cannot handle the resource directly,
            // so extract it into the local cache directory.
            InputStream asset = this.getResources().openRawResource(FILERESID);
            FileOutputStream output = new FileOutputStream(file);
            final byte[] buffer = new byte[1024];
            int size;
            while ((size = asset.read(buffer)) != -1) {
                output.write(buffer, 0, size);
            }
            asset.close();
            output.close();
        }
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);

        // capture PDF data
        // all this just to get a handle to the actual PDF representation
        if (parcelFileDescriptor != null) {
            pdfRenderer = new PdfRenderer(parcelFileDescriptor);
        }
    }

    // do this before you quit!
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void closeRenderer() throws IOException {
        if (null != currentPage) {
            currentPage.close();
        }
        pdfRenderer.close();
        parcelFileDescriptor.close();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void showPage(int index) {
//        System.out.println("show 1");
        if (pdfRenderer.getPageCount() <= index || index < 0) { // check if index in range of pdf
            return;
        }
//        System.out.println("show 2");

        // Close the current page before opening another one.
        if (null != currentPage) {
            currentPage.close();
        }
        // Use `openPage` to open a specific page in PDF.
        currentPage = pdfRenderer.openPage(index);
        // Important: the destination bitmap must be ARGB (not RGB).
        Bitmap bitmap = Bitmap.createBitmap(currentPage.getWidth(), currentPage.getHeight(), Bitmap.Config.ARGB_8888);

        // Here, we render the page onto the Bitmap.
        // To render a portion of the page, use the second and third parameter. Pass nulls to get the default result.
        // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
        currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

        // Display the page
        pageImage.setImage(bitmap);

        // Display page number
        TextView filenameView = findViewById(R.id.pageNum);
        filenameView.setText("Page " + (index + 1) + " / " + pdfRenderer.getPageCount());
        // Update page number
        pageImage.setPageNum(index);
    }
}
