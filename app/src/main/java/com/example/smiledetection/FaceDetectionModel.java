package com.example.smiledetection;

public class FaceDetectionModel {
    private int id;
    private String text;

    public FaceDetectionModel() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public FaceDetectionModel(int id, String text) {
        this.id = id;
        this.text = text;
    }
}
