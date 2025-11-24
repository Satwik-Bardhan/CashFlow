package com.satvik.cashflow;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class HelpSupportActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_support);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Back Button
        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        // NOTE: In a real app, you would find these views by ID and set listeners.
        // Since the XML I provided earlier used a generic structure inside CardViews
        // without specific IDs for the "Contact Us" or "Report Bug" click areas,
        // you might need to add IDs to those specific layouts in your XML if you want
        // them to be clickable.

        // Example: If you add android:id="@+id/btnContactUs" to the CardView in XML:
        // findViewById(R.id.btnContactUs).setOnClickListener(v -> sendEmail("support@cashflow.com", "Support Request"));
    }

    private void sendEmail(String recipient, String subject) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{recipient});
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        try {
            startActivity(Intent.createChooser(intent, "Send Email"));
        } catch (Exception e) {
            Toast.makeText(this, "No email client installed", Toast.LENGTH_SHORT).show();
        }
    }
}