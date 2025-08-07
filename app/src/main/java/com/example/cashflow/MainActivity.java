package com.example.cashflow;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // This activity has no UI, it just decides where to go next.
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            // No user is signed in, go to the Signin screen.
            startActivity(new Intent(this, Signin.class));
        } else {
            // A user is already signed in, go to the HomePage.
            startActivity(new Intent(this, HomePage.class));
        }

        // Finish this activity so the user can't press "back" to get to it.
        finish();
    }
}
