FROM python:3.11-slim

WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY taihu_toolkit ./taihu_toolkit
COPY taihu_toolkit.LICENSE ./taihu_toolkit.LICENSE

ENV PYTHONPATH=/srv
EXPOSE 9100
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "9100"]
