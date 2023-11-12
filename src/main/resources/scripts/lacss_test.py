from pathlib import Path
import numpy as np
from lacss.deploy import Predictor, model_urls
from lacss.utils import show_images
from skimage.transform import rescale
from skimage.color import label2rgb
import imageio.v2 as imageio
import matplotlib.pyplot as plt

from jax.lib import xla_bridge

print(xla_bridge.get_backend().platform)

predictor = Predictor(model_urls["tissuenet"])
    
cd = Path(__file__).parent
image = imageio.imread( cd / '3.tif' )
image = rescale(image.astype("float32"), 2)
image -= image.mean()
image /= image.std()
image = image[..., None]

pred = predictor.predict_label(image)
pred = np.asarray(pred)

show_images([
    image,
    label2rgb(pred, bg_label = 0),
])

plt.show()

##Runtime is ridiciousl; needs a progress bar