package com.plotpals.client;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.plotpals.client.data.Garden;
import com.plotpals.client.data.Post;
import com.plotpals.client.data.Profile;
import com.plotpals.client.data.Role;
import com.plotpals.client.data.RoleEnum;
import com.plotpals.client.data.Task;
import com.plotpals.client.utils.GoogleProfileInformation;
import com.plotpals.client.utils.TaskSocketHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.socket.client.Socket;

public class ForumBoardMainActivity extends NavBarActivity {

    final static String TAG = "ForumBoardMainActivity";
    private Integer currentGardenId;
    private String currentGardenName = "This should not show up";
    private int upperPosts = 0;
    boolean isPlotOwner = false;
    boolean isGardenOwner = false;
    boolean isCaretaker = false;
    double myRating = 0; // we set this later. if no tasks show up, this int might still be 0.

    Socket taskSocket;

    GoogleProfileInformation googleProfileInformation;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forum_board_main);
        loadExtras();
        activateNavBar();
        taskSocket = TaskSocketHandler.getSocket();
        taskSocket.connect();

        TextView newPostText = findViewById(R.id.forum_board_new_post);
        TextView newTaskText = findViewById(R.id.forum_board_new_task);
        View greyScreenOverlay = findViewById(R.id.plus_grey_screen);
        newPostText.setVisibility(View.GONE);
        newTaskText.setVisibility(View.GONE);
        greyScreenOverlay.setVisibility(View.GONE);

        Log.d(TAG, "Garden Name:" + currentGardenName);
        TextView name = findViewById(R.id.forum_board_garden_name);
        name.setText(currentGardenName);

        ImageView arrow = findViewById(R.id.forum_board_arrow);
        arrow.setOnClickListener(view -> finish());

        greyScreenOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (greyScreenOverlay.getVisibility() == View.VISIBLE) {
                    newTaskText.setVisibility(View.GONE);
                    greyScreenOverlay.setVisibility(View.GONE);
                }
            }
        });

        ImageView plus = findViewById(R.id.forum_board_plus);
        plus.setOnClickListener(view -> {
            Log.d(TAG, "Clicking Plus Sign");
            // We disable posts for now
            // newPostText.setVisibility(newPostText.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            newTaskText.setVisibility(newTaskText.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            greyScreenOverlay.setVisibility(greyScreenOverlay.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });

        newPostText.setOnClickListener(view -> {
            Log.d(TAG, "Clicking New Post");
            Intent intent = new Intent(ForumBoardMainActivity.this, ForumBoardNewPostActivity.class);
            googleProfileInformation.loadGoogleProfileInformationToIntent(intent);
            intent.putExtra("gardenId", currentGardenId);
            intent.putExtra("gardenName", currentGardenName);
            startActivity(intent);
        });

        newTaskText.setOnClickListener(view -> {
            Log.d(TAG, "Clicking New Task");
            Intent intent = new Intent(ForumBoardMainActivity.this, ForumBoardNewTaskActivity.class);
            googleProfileInformation.loadGoogleProfileInformationToIntent(intent);
            intent.putExtra("gardenId", currentGardenId);
            intent.putExtra("gardenName", currentGardenName);
            startActivity(intent);
        });
        findViewById(R.id.forum_board_plus).setVisibility(View.GONE);

        taskSocket.on("New Task", args -> {
            if (args[0] != null) {
                int idOfGardenWithNewTask = (int) args[0];

                if(idOfGardenWithNewTask == currentGardenId) {
                    loadPosts();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        taskSocket.close();
    }

    @Override
    protected void onStart() {
        super.onStart();
        requestMembers(currentGardenId);
        loadPosts();
    }

    private void loadPosts() {

        RequestQueue volleyQueue = Volley.newRequestQueue(this);

        Log.d(TAG, "Current Garden: " + currentGardenId);
        String gardenUrl = "http://10.0.2.2:8081/posts/all?gardenId="+ currentGardenId;
        Request<?> jsonObjectGardenRequest = new JsonObjectRequest(
                Request.Method.GET,
                gardenUrl,
                null,
                (JSONObject response) -> {
                    try {
                        Log.d(TAG, "Obtaining Posts");
                        JSONArray fetchedPosts = (JSONArray)response.get("data");
                        Log.d(TAG, "Posts: " + fetchedPosts.length());

                        /* Resets scrollable view so that it does not have items */
                        RelativeLayout layout = findViewById(R.id.forum_board_scrollview_layout);
                        layout.removeAllViewsInLayout();
                        upperPosts = 0;

                        // loop and add every garden
                        for (int i = 0; i < fetchedPosts.length(); i++) {
                            // Probably want to check if task or post, but we only do task for now
                            Post task = new Post(fetchedPosts.getJSONObject(i));

                            // Check if we exceed minimum rating
                            Log.d(TAG, "My Rating: " + myRating);
                            if (myRating > task.getTask().getMinimumRating()) {
                                addTask(task);
                                upperPosts++;
                            }
                        }
                        Bundle extras = getIntent().getExtras();
                        assert extras != null;
                    } catch (JSONException e) {
                        Log.d(TAG, e.toString());
                    }
                },
                (VolleyError e) -> {
                    Log.d(TAG, e.toString());
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + googleProfileInformation.getAccountIdToken());
                return headers;
            }
        };

        String profileUrl = "http://10.0.2.2:8081/profiles/all?profileId="+ googleProfileInformation.getAccountUserId();
        Request<?> jsonObjectProfileRequest = new JsonObjectRequest(
                Request.Method.GET,
                profileUrl,
                null,
                (JSONObject response) -> {
                    try {
                        Log.d(TAG, "Obtaining Profile");
                        JSONArray fetchedProfiles = (JSONArray)response.get("data");
                        Log.d(TAG, "Profiles (should be 1): " + fetchedProfiles.length());
                        Profile profile = new Profile(fetchedProfiles.getJSONObject(0));
                        Log.d(TAG, "Grabbing My Rating: " + profile.getRating());
                        myRating = profile.getRating();
                        Bundle extras = getIntent().getExtras();
                        assert extras != null;
                        volleyQueue.add(jsonObjectGardenRequest);
                    } catch (JSONException e) {
                        Log.d(TAG, e.toString());
                    }
                },
                (VolleyError e) -> {
                    Log.d(TAG, e.toString());
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + googleProfileInformation.getAccountIdToken());
                return headers;
            }
        };

        volleyQueue.add(jsonObjectProfileRequest);
    }

    private void requestMembers(Integer gardenId) {
        RequestQueue volleyQueue = Volley.newRequestQueue(this);
        String url = String.format("http://10.0.2.2:8081/roles/all?gardenId=%s", gardenId);

        Request<?> jsonObjectRequest = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,

                (JSONObject response) -> {
                    try {
                        Log.d(TAG, "Obtaining members");
                        JSONArray fetchedMembers = (JSONArray)response.get("data");

                        /* Populate taskList with fetched task and notify the TaskListView UI to display the fetched task*/
                        if(fetchedMembers.length() > 0) {
                            for (int i = 0; i < fetchedMembers.length(); i++) {
                                JSONObject roleJsonObject = fetchedMembers.getJSONObject(i);
                                Role role = new Role(roleJsonObject);
                                View plusButton = findViewById(R.id.forum_board_plus);
                                if (Objects.equals(role.getProfileId(), googleProfileInformation.getAccountUserId())) {
                                    if (role.getRoleNum() == RoleEnum.PLOT_OWNER) {
                                        isPlotOwner = true;
                                        plusButton.setVisibility(View.VISIBLE);
                                    }
                                    else if (role.getRoleNum() == RoleEnum.CARETAKER) {
                                        isCaretaker = true;
                                        plusButton.setVisibility(View.GONE);
                                    }
                                    else if (role.getRoleNum() == RoleEnum.GARDEN_OWNER) {
                                        isGardenOwner = true;
                                        plusButton.setVisibility(View.VISIBLE);
                                    }
                                    break;
                                }
                            }
                        }
                    } catch (JSONException e) {
                        Log.d(TAG, e.toString());
                    }
                },
                (VolleyError e) -> {
                    Log.d(TAG, e.toString());
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + googleProfileInformation.getAccountIdToken());
                return headers;
            }
        };

        volleyQueue.add(jsonObjectRequest);
    }

    private void addTask(Post task) {

        RelativeLayout layout = findViewById(R.id.forum_board_scrollview_layout);
        LayoutInflater layoutInflater = (LayoutInflater)
                this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = layoutInflater.inflate(R.layout.activity_forum_board_task_preview, layout, false);

        defineMargins(view, upperPosts);

        setPreviewText(view, task);

        setTaskButton(view, task);

        layout.addView(view);

    }

    private void setTaskButton(View view, Post task) {
        TextView title = view.findViewById(R.id.forum_board_task_preview_title);
        title.setOnClickListener(v -> {
            Log.d(TAG, "Clicking Task Title");
            Intent intent = new Intent(ForumBoardMainActivity.this, ForumBoardViewTaskActivity.class);
            googleProfileInformation.loadGoogleProfileInformationToIntent(intent);
            intent.putExtra("postId", task.getId());
            startActivity(intent);
        });
    }

    private void setPreviewText(View view, Post task) {
        TextView title = view.findViewById(R.id.forum_board_task_preview_title);
        title.setText(task.getTitle());
        TextView description = view.findViewById(R.id.forum_board_task_preview_description);
        description.setText("Description: " + task.getDescription());
        TextView author = view.findViewById(R.id.forum_board_task_preview_author);
        author.setText("Author: " + task.getAssignerName());
    }

    private void defineMargins (View v, int upperPosts) {
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        margins.setMargins(margins.leftMargin, margins.topMargin + upperPosts * 350, margins.rightMargin, margins.bottomMargin);
    }

    /**
     * load extras forwarded from previous activity
     */
    private void loadExtras() {
        Bundle extras = getIntent().getExtras();

        if (extras != null) {
            googleProfileInformation = new GoogleProfileInformation(extras);
            currentGardenId = extras.getInt("gardenId");
            currentGardenName = extras.getString("gardenName");
        }
    }
}