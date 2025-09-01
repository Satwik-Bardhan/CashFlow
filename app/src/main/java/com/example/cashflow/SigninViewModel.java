package com.example.cashflow;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class SigninViewModel extends ViewModel {

    private final FirebaseAuth mAuth;

    private final MutableLiveData<FirebaseUser> _user = new MutableLiveData<>();
    public LiveData<FirebaseUser> user = _user;

    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public LiveData<String> error = _error;

    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>();
    public LiveData<Boolean> loading = _loading;

    public SigninViewModel() {
        mAuth = FirebaseAuth.getInstance();
    }

    public void signInWithEmail(String email, String password) {
        _loading.setValue(true);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        _user.setValue(mAuth.getCurrentUser());
                    } else {
                        _error.setValue(task.getException() != null ? task.getException().getMessage() : "Authentication failed.");
                    }
                    _loading.setValue(false);
                });
    }

    public void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        _loading.setValue(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        _user.setValue(mAuth.getCurrentUser());
                    } else {
                        _error.setValue(task.getException() != null ? task.getException().getMessage() : "Google sign-in failed.");
                    }
                    _loading.setValue(false);
                });
    }

    public void signInAnonymously() {
        _loading.setValue(true);
        mAuth.signInAnonymously()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        _user.setValue(mAuth.getCurrentUser());
                    } else {
                        _error.setValue(task.getException() != null ? task.getException().getMessage() : "Guest sign-in failed.");
                    }
                    _loading.setValue(false);
                });
    }
}

