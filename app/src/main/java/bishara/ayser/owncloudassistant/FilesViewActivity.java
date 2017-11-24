package bishara.ayser.owncloudassistant;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.menu.ExpandedMenuView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.OwnCloudClientFactory;
import com.owncloud.android.lib.common.authentication.OwnCloudCredentialsFactory;
import com.owncloud.android.lib.common.network.OnDatatransferProgressListener;
import com.owncloud.android.lib.common.operations.OnRemoteOperationListener;
import com.owncloud.android.lib.common.operations.RemoteOperation;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.resources.files.ChunkedUploadRemoteFileOperation;
import com.owncloud.android.lib.resources.files.DownloadRemoteFileOperation;
import com.owncloud.android.lib.resources.files.FileUtils;
import com.owncloud.android.lib.resources.files.ReadRemoteFolderOperation;
import com.owncloud.android.lib.resources.files.RemoteFile;
import com.owncloud.android.lib.resources.files.RemoveRemoteFileOperation;
import com.owncloud.android.lib.resources.files.UploadRemoteFileOperation;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Jeries on 10/5/2017.
 */

public class FilesViewActivity extends AppCompatActivity {

    private OwnCloudClient mClient;
    private Handler mHandler = new Handler();

    private ArrayList<String> filepathsview = new ArrayList<>();
    private ArrayList<RemoteFile> files = new ArrayList<>();
    private fileListItemAdapter arrayAdapter;
    private String currentFolder;
    private String name;
    private String password;
    private SwipeRefreshLayout filesSwipeRefresh;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filesview);

        Intent myIntern = getIntent();
        name = myIntern.getStringExtra("name");
        password = myIntern.getStringExtra("pass");
        currentFolder = myIntern.getStringExtra("currentFolder");

        setTitle(currentFolder);

        Uri serverUri = Uri.parse(getString(R.string.server_base_url));

        //arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, filepathsview);
        arrayAdapter = new fileListItemAdapter(this, R.layout.file_list_item, filepathsview);
        ListView listview = (ListView) findViewById(R.id.filesListView);
        listview.setAdapter(arrayAdapter);
        mClient = OwnCloudClientFactory.createOwnCloudClient(
                serverUri,
                this,
                // Activity or Service context
                true);

        mClient.setCredentials(
                OwnCloudCredentialsFactory.newBasicCredentials(
                        name,
                        password
                )
        );

        refreshListView(currentFolder);

        FloatingActionButton uploadButton = (FloatingActionButton) findViewById(R.id.uploadFile);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChooser(currentFolder);
            }
        });

        filesSwipeRefresh = (SwipeRefreshLayout) findViewById(R.id.filesSwipeRefresh);
        filesSwipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshListView(currentFolder);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (filepathsview.contains("..")) {
            Intent intent = new Intent(getBaseContext(), FilesViewActivity.class);
            intent.putExtra("name", name);
            intent.putExtra("pass", password);
            intent.putExtra("currentFolder", FileUtils.getParentPath(currentFolder));
            startActivity(intent);
        } else {
            Toast.makeText(getBaseContext(), "Nowhere to go back...", Toast.LENGTH_LONG).show();
        }

    }

    public void refreshListView(final String currentFolder) {
        filepathsview.clear();
        files.clear();
        ReadRemoteFolderOperation refreshOperation = new ReadRemoteFolderOperation(currentFolder);
        refreshOperation.execute(mClient, new OnRemoteOperationListener() {
            @Override
            public void onRemoteOperationFinish(RemoteOperation caller, RemoteOperationResult result) {
                for(Object obj: result.getData()) {
                    RemoteFile file = (RemoteFile) obj;
                    if (file.getRemotePath().equals("/")) {
                        // NOTHING!
                    } else {
                        if (file.getMimeType() == "DIR" && file.getRemotePath().equals(currentFolder)) {
                            filepathsview.add("..");
                        } else {
                            filepathsview.add(file.getRemotePath().substring(currentFolder.length()));
                        }
                        files.add(file);
                    }
                }

                arrayAdapter.notifyDataSetChanged();
                filesSwipeRefresh.setRefreshing(false);
            }
        }, mHandler);
    }

    public void downloadFile(final RemoteFile remoteFile, String username) {
        // Create folder
        final String path =  "/owncloud/downloads/" + username;
        final File privateFolder = Environment.getExternalStorageDirectory();
        final File folder = new File(privateFolder.getAbsolutePath() + path);
        folder.mkdirs();

        DownloadRemoteFileOperation downloadOperation = new DownloadRemoteFileOperation(remoteFile.getRemotePath(), folder.getAbsolutePath());
        downloadOperation.execute(mClient, new OnRemoteOperationListener() {
            @Override
            public void onRemoteOperationFinish(RemoteOperation caller, RemoteOperationResult result) {
                Toast.makeText(getBaseContext(), (result.isSuccess() ? "Downloaded to "+ privateFolder.getAbsolutePath() + path + remoteFile.getRemotePath() : "Downloading has failed"), Toast.LENGTH_LONG).show();
            }
        }, mHandler);
    }

    public void uploadFile(final File file, final String remotePath, final String currentFolder) {
        String storagePath = file.getAbsolutePath();
        String fileLastModifTimestamp = getFileLastModifTimeStamp(storagePath);

        UploadRemoteFileOperation uploadOperation;
        int index = file.getName().lastIndexOf(".");
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.getName().substring(index + 1).toLowerCase());
        if (file.length() > ChunkedUploadRemoteFileOperation.CHUNK_SIZE ) {
            uploadOperation = new ChunkedUploadRemoteFileOperation(
                    storagePath, remotePath, mimeType, fileLastModifTimestamp
            );
        } else {
            uploadOperation = new UploadRemoteFileOperation(
                    storagePath, remotePath, mimeType, fileLastModifTimestamp
            );
        }

        uploadOperation.execute(mClient, new OnRemoteOperationListener() {
            @Override
            public void onRemoteOperationFinish(RemoteOperation caller, RemoteOperationResult result) {
                Toast.makeText(getBaseContext(), (result.isSuccess() ? "Uploaded to " + remotePath : "Uploading has failed"), Toast.LENGTH_LONG).show();
                refreshListView(currentFolder);
            }
        }, mHandler);
    }

    private static final int FILE_SELECT_CODE = 0;

    private void showChooser(String currentFolder) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Select a File to upload"),
                    FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case FILE_SELECT_CODE:
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();
                    String path = FileUtils.getPath(this, uri);
                    File file = new File(path);
                    uploadFile(file, currentFolder + file.getName(), currentFolder);
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private static String getFileLastModifTimeStamp (String storagePath) {
        File file = new File(storagePath);
        Long timeStampLong = file.lastModified()/1000;
        return timeStampLong.toString();
    }

    public void deleteRemoteFile(int position){
        final RemoteFile remoteFile = files.get(position);

        RemoveRemoteFileOperation downloadOperation = new RemoveRemoteFileOperation(remoteFile.getRemotePath());
        downloadOperation.execute(mClient, new OnRemoteOperationListener() {
            @Override
            public void onRemoteOperationFinish(RemoteOperation caller, RemoteOperationResult result) {
                Toast.makeText(getBaseContext(), (result.isSuccess() ? "Removed file "+ remoteFile.getRemotePath() : "Removing file has failed"), Toast.LENGTH_LONG).show();
                refreshListView(currentFolder);
            }
        }, mHandler);
    }

    public void clickFile(int position){
        RemoteFile file = files.get(position);
        String filepath = file.getRemotePath();
        if(file.getMimeType() == "DIR"){
            Intent intent = new Intent(getBaseContext(), FilesViewActivity.class);
            intent.putExtra("name", name);
            intent.putExtra("pass", password);
            if (filepathsview.get(position) == "..") {
                intent.putExtra("currentFolder", FileUtils.getParentPath(currentFolder));
            } else {
                intent.putExtra("currentFolder", filepath);
            }
            startActivity(intent);
        }else{
            downloadFile(file, name);
        }
    }

    private class fileListItemAdapter extends ArrayAdapter<String> {
        private int layout;
        private final FilesViewActivity myActivity;
        public fileListItemAdapter(@NonNull FilesViewActivity context, @LayoutRes int resource, @NonNull List<String> objects) {
            super(context, resource, objects);
            layout = resource;
            myActivity = context;
        }

        @NonNull
        @Override
        public View getView(final int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            viewHolder viewHolder = null;
            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(getContext());
                convertView = inflater.inflate(layout, parent, false);
                viewHolder = new viewHolder();
                viewHolder.thumbnail = (ImageView) convertView.findViewById(R.id.file_list_item_thumbnail);
                viewHolder.title = (TextView) convertView.findViewById(R.id.file_list_item_name);
                viewHolder.type = (TextView) convertView.findViewById(R.id.file_list_item_type);
                viewHolder.delete_button = (ImageButton) convertView.findViewById(R.id.file_list_item_delete_button);
                viewHolder.delete_button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        myActivity.deleteRemoteFile(position);
                    }
                });
                viewHolder.title.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        myActivity.clickFile(position);
                    }
                });
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (viewHolder) convertView.getTag();
            }
            viewHolder.title.setText(getItem(position));
            viewHolder.type.setText(files.get(position).getMimeType().toString());
            RemoteFile file = files.get(position);
            if(file.getMimeType() == "DIR") {
                viewHolder.thumbnail.setImageResource(getResources().getIdentifier("@android:drawable/ic_menu_agenda", null, null));
                if (getItem(position) == "..") {
                    viewHolder.delete_button.setVisibility(View.GONE);
                } else {
                    viewHolder.delete_button.setVisibility(View.VISIBLE);
                }
            } else {
                viewHolder.thumbnail.setImageResource(getResources().getIdentifier("@android:drawable/ic_menu_gallery", null, null));
            }

            return convertView;
        }

        public class viewHolder {
            ImageView thumbnail;
            TextView title;
            TextView type;
            ImageButton delete_button;
        }
    }
}
