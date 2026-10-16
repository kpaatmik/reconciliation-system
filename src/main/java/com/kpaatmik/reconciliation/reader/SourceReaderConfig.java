package com.kpaatmik.reconciliation.reader;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.LineMapper;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import com.kpaatmik.reconciliation.config.ReconciliationFileResolver;
import com.kpaatmik.reconciliation.dto.SourceCsvRecord;

@Configuration
public class SourceReaderConfig {

	@Bean
	@StepScope
	public FlatFileItemReader<SourceCsvRecord> sourceReader(ReconciliationFileResolver fileResolver) {

		DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
		tokenizer.setDelimiter(",");
		tokenizer.setQuoteCharacter('"');
		tokenizer.setNames("transaction_id", "account_number", "transaction_date", "amount", "currency",
				"transaction_type", "description");

		LineMapper<SourceCsvRecord> lineMapper = (line, lineNumber) -> {
			try {
				FieldSet fields = tokenizer.tokenize(line);

				return new SourceCsvRecord(lineNumber, fields.readString("transaction_id"),
						fields.readString("account_number"), fields.readString("transaction_date"),
						fields.readString("amount"), fields.readString("currency"),
						fields.readString("transaction_type"), fields.readString("description"));
			} catch (Exception ex) {
				throw new IllegalArgumentException("Unable to parse source CSV at line " + lineNumber, ex);
			}
		};

		FlatFileItemReader<SourceCsvRecord> reader = new FlatFileItemReaderBuilder<SourceCsvRecord>()

				.name("sourceReader").resource(new FileSystemResource(fileResolver.getSourceFile())).linesToSkip(1)
				.lineMapper(lineMapper).saveState(true).strict(true).build();

		return reader;
	}
}
