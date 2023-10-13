#pip install lacss

import lacss
from lacss.deploy import Predictor

from skimage.transform import rescale
import imageio.v2 as imageio

path = ''#dir of files

model_path = '' #cp path to model


predictor = Predictor(model_path)

for file in path:
    image = imageio.imread(file)
    img = rescale(image.astype("float32"), 2)
    img -= img.mean()
    img /= img.std()
    img = img[..., None]

    #pred = predictor.predict(img)
    pred_label = predictor.predict_label(img)


## Probably requires a re-formmating of output