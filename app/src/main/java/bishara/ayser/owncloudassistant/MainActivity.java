package bishara.ayser.owncloudassistant;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.View;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.OwnCloudClientFactory;

import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;

import java.security.GeneralSecurityException;

public class MainActivity extends AppCompatActivity {

    private OwnCloudClient mClient;
    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);

        }

            Uri serverUri = Uri.parse(getString(R.string.server_base_url));
        Protocol pr = Protocol.getProtocol("https");
        if (pr == null || !(pr.getSocketFactory() instanceof SelfSignedConfidentSslSocketFactory)) {
            try {
                ProtocolSocketFactory psf = new SelfSignedConfidentSslSocketFactory();
                Protocol.registerProtocol(
                        "https",
                        new Protocol("https", psf, 443));
            }catch (GeneralSecurityException e) {
                e.printStackTrace();
            }
        }
        // Create client object to perform remote operations
        mClient = OwnCloudClientFactory.createOwnCloudClient(
                serverUri,
                this,
                // Activity or Service context
                true);

        Button x = (Button) findViewById(R.id.button);
        x.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView nameView = (TextView) findViewById(R.id.username);
                TextView passwordView = (TextView) findViewById(R.id.password);
                String name = nameView.getText().toString();
                String password = passwordView.getText().toString();

                signIn("test ", "juju");
            }
        });
//        ListView x = (ListView) findViewById(R.id.myList);
//        ArrayList<String> array = new ArrayList<String>();
//        array.add("abc");
//        array.add("abc");
//        array.add("abc");
//        array.add("abc");
//        array.add("abc");
//        array.add("abc");
//        array.add("abc");
//        ArrayAdapter<String> ar = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, array);
//        x.setAdapter(ar);
//        ar.notifyDataSetChanged();
    }

    private void signIn(String name, String password) {
        Intent x = new Intent(getBaseContext(), FilesViewActivity.class);
        x.putExtra("name",name);
        x.putExtra("pass",password);
        x.putExtra("currentFolder", "/");
        startActivity(x);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
