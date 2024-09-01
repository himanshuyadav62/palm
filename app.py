from flask import Flask, Response, request
from flask_cors import CORS
import requests
import json

app = Flask(__name__)
CORS(app)

@app.route('/generate', methods=['POST'])
def generate():
    prompt = request.json['prompt']

    def stream_response():
        response = requests.post(
            'http://localhost:11434/api/generate',
            json={
                'model': 'qwen2:0.5b',
                'prompt': prompt
            },
            stream=True
        )

        for line in response.iter_lines():
            if line:
                yield f"data: {line.decode('utf-8')}\n\n"

    return Response(stream_response(), content_type='text/event-stream')

if __name__ == '__main__':
    app.run(debug=True)