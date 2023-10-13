#pip install lacss

import lacss
import imageio.v2 as imageio

path = #dir of files

model_path = #cp path to model


predictor = Predictor(cp_path)

for file in path:
    image = image.imread(file)
    img = rescale(image.astype("float32"), 2)
    img -= img.mean()
    img /= img.std()
    img = img[..., None]

    #pred = predictor.predict(img)
    pred_label = predictor.predict_label(img)


## Probably requires a re-formmating of output