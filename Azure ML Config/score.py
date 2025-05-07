import json
import numpy as np
import tensorflow as tf
from PIL import Image
import io
import base64

def init():
    global model
    model = tf.keras.models.load_model('model')
    
def preprocess_image(image_bytes):
    # Convert bytes to PIL Image
    image = Image.open(io.BytesIO(image_bytes))
    
    # Convert to RGB if image is in RGBA
    if image.mode == 'RGBA':
        image = image.convert('RGB')
    
    # Resize image to match model's expected sizing
    image = image.resize((224, 224))
    
    # Convert image to numpy array
    image_array = np.array(image)
    
    # Ensure we have 3 channels
    if len(image_array.shape) == 2:  # If grayscale
        image_array = np.stack((image_array,)*3, axis=-1)
    elif image_array.shape[-1] == 4:  # If RGBA
        image_array = image_array[:,:,:3]
    
    # Normalize pixel values
    normalized_image_array = image_array.astype(np.float32) / 255.0
    
    # Create batch dimension
    return np.expand_dims(normalized_image_array, axis=0)

def run(raw_data):
    try:
        # Parse input data
        data = json.loads(raw_data)
        image_b64 = data['image']
        
        # Decode base64 image
        image_bytes = base64.b64decode(image_b64)
        
        # Preprocess the image
        processed_image = preprocess_image(image_bytes)
        
        # Make prediction
        predictions = model.predict(processed_image)
        
        # Get class names from labels.txt
        with open('model/labels.txt', 'r') as f:
            class_names = [line.strip() for line in f.readlines()]
        
        # Get predicted class and confidence
        predicted_class_index = np.argmax(predictions[0])
        confidence = float(predictions[0][predicted_class_index])
        
        # Prepare response
        result = {
            'class_name': class_names[predicted_class_index],
            'confidence': confidence,
            'all_probabilities': {
                class_name: float(prob) 
                for class_name, prob in zip(class_names, predictions[0])
            }
        }
        
        return json.dumps(result)
        
    except Exception as e:
        error = str(e)
        return json.dumps({"error": error})