package com.example.learnly;

public class MathQuestion {

    private String questionText;
    private int answer;
    private String fingerTrick;

    // Constructor to create a new MathQuestion object
    public MathQuestion(String questionText, int answer, String fingerTrick) {
        this.questionText = questionText;
        this.answer = answer;
        this.fingerTrick = fingerTrick;
    }

    // Provides the question text (e.g., "5 + 3")
    public String getQuestionText() {
        return questionText;
    }

    // Provides the correct answer (e.g., 8)
    public int getAnswer() {
        return answer;
    }

    // Provides the hint text
    public String getFingerTrick() {
        return fingerTrick;
    }
}