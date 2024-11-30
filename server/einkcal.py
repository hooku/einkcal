from PIL import Image
from PIL import ImageFont
from PIL import ImageDraw

from datetime import datetime
from zhdate import ZhDate

import os
import random

birth = datetime(2023, 12, 21)
interval = datetime.now() - birth

year = datetime.now().year
month = datetime.now().month
day = datetime.now().day
weekday = datetime.now().weekday()
weekList = ["一","二","三","四","五","六","日"]

zhdate = ZhDate.from_datetime(datetime.now())

image = Image.new(mode="L", size=(800, 600), color=255)
imageDraw = ImageDraw.Draw(image)

# get weather
os.system("weathercn -f msyh.ttf 101020100")

# chinese date
imageFontChinese = ImageFont.truetype("msyh.ttf", 50)
imageDraw.text((535, 10), "{}".format((zhdate.chinese()[5:])).replace(" ", "\n"), color=0, font=imageFontChinese)

# birth interval
imageFontBirth = ImageFont.truetype("msyh.ttf", 60)
imageDraw.text((535, 420), "宝宝出生\n第{}天".format(interval.days), color=0, font=imageFontBirth) 

# eng date
imageFontEng = ImageFont.truetype("msyh.ttf", 55)
imageDraw.text((20, 10), "{:d}月{:d}日 周{}".format(month, day, weekList[weekday]), color=0, font=imageFontEng)

# weather
imageWeather = Image.open("C:\\Users\\Administrator\\.cache\\weatherCN\\weather.png")
(largeWidth, largeHeight) = (int(imageWeather.width*1.7), int(imageWeather.height*1.7))
imageWeatherLarge = imageWeather.resize((largeWidth, largeHeight), Image.LANCZOS)
image.paste(imageWeatherLarge, (20, 60), mask=imageWeatherLarge)

# name
#fname = open('C:\\Users\\Administrator\\Documents\\cmd\\name.txt', encoding="UTF-8")
#lines = fname.readlines()
#linecount = len(lines)
#names = []
#for ii in range(0, 3):
#    line = random.randint(0, linecount - 1)
#    names.append(lines[line])

#nameTxt = "".join(names)

#imageFontName = ImageFont.truetype("msyh.ttf", 55)
#imageDraw.text((580, 210), "{}".format(nameTxt), color=0, font=imageFontName)

image = image.rotate(270, expand=1)

image.save("1.png")
